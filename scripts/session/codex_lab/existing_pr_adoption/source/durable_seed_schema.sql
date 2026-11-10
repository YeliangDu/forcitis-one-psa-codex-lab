
    create table installation(id integer primary key check(id=1), pin text not null);
    create table owner(id integer primary key check(id=1), generation integer not null, client text, token_sha text, paused integer not null check(paused in (0,1)));
    create table tasks(revision integer primary key,body text not null,phase text not null,capability_epoch integer not null,fence_seq integer);
    create table turns(turn_id text primary key,revision integer not null,owner_generation integer not null,epoch integer not null,state text not null);
    create table effects(effect_id text primary key,revision integer not null,generation integer not null,operation_key text unique not null,kind text not null,target text not null,phase text not null,issue_count integer not null default 0,observation text,advances integer not null default 0);
    create table waits(wait_id text primary key,effect_id text not null,revision integer not null,generation integer not null,session_id text not null,turn_id text not null,call_id text not null,connection_id text not null,phase text not null,completions integer not null default 0,unique(session_id,turn_id,call_id));
    create table events(seq integer primary key autoincrement,kind text not null,body text not null);
    create table role_registration(id integer primary key check(id=1),effect_id text unique not null,phase text not null,generation integer not null,session_id text unique,first_turn_id text,readback_sha256 text);
    create table native_dispatch(effect_id text primary key,session_id text not null,revision integer not null,generation integer not null,epoch integer not null,prompt_sha256 text not null,phase text not null,connection_id text not null,turn_id text);
    create table native_tool_operations(session_id text not null,turn_id text not null,call_id text not null,revision integer not null,generation integer not null,epoch integer not null,connection_id text not null,phase text not null,authorization_seq integer,execution_seq integer,result text,primary key(session_id,turn_id,call_id));
    