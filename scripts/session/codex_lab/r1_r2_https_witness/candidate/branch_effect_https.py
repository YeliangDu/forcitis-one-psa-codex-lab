"""Explicit branch-only HTTPS transport candidate; not an SSH fallback."""
from pathlib import Path
import os
import shlex
import stat

from branch_effect_port import BranchEffect, REPO, need, physical, pinned, run_bounded

class BranchEffectHTTPS(BranchEffect):
    origin='https://github.com/'+REPO+'.git'

    def __init__(self,subject,installation):
        need(installation.get('transport_kind')=='https-gh-exact','explicit-https-installation-required')
        need('ssh_agent_socket' not in installation and 'ssh_agent_dev_inode' not in installation,
             'mixed-ssh-https-installation')
        super().__init__(subject,installation)

    def _git(self,*args):
        self._verify_config()
        config=physical(self.i['gh_config_dir'])
        info=config.stat()
        need(config.is_dir() and info.st_uid==os.geteuid() and not stat.S_IMODE(info.st_mode)&0o022
             and [info.st_dev,info.st_ino]==self.i['gh_config_dev_inode'],'gh-config-identity-drift')
        helper=physical(self.i['credential_helper'])
        need(helper.name=='exact_gh_credential.py','wrong-credential-helper')
        pinned(str(helper),self.i['credential_helper_sha256'])
        python=physical(self.i['credential_python'])
        need(python.is_file() and os.access(python,os.X_OK),'credential-interpreter-unavailable')
        helper_command='!exec '+shlex.join([str(python),'-I','-B',str(helper)])
        env={'PATH':'/usr/bin:/bin','HOME':str(self.empty_home),'GH_CONFIG_DIR':str(config),
             'GIT_CONFIG_NOSYSTEM':'1','GIT_CONFIG_GLOBAL':'/dev/null','GIT_TERMINAL_PROMPT':'0',
             'GIT_OPTIONAL_LOCKS':'0'}
        argv=['/usr/bin/git','--no-replace-objects','--git-dir='+str(self.gitdir),'--work-tree='+str(self.root),
              '-c','core.hooksPath=/dev/null','-c','protocol.allow=never','-c','protocol.https.allow=always',
              '-c','http.sslVerify=true','-c','http.followRedirects=false',
              '-c','credential.helper=','-c','credential.useHttpPath=true',
              '-c','credential.https://github.com.helper='+helper_command,*args]
        return run_bounded(argv,cwd=self.root,env=env)
