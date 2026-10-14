-- ===== q1.sql
##01_qz_tables
SELECT t.name, SUM(p.rows) FROM u8cloud_QZ.sys.tables t JOIN u8cloud_QZ.sys.partitions p ON p.object_id=t.object_id AND p.index_id IN (0,1) GROUP BY t.name HAVING SUM(p.rows)>0 ORDER BY t.name
##02_sf_tables
SELECT t.name, SUM(p.rows) FROM u8cloud.sys.tables t JOIN u8cloud.sys.partitions p ON p.object_id=t.object_id AND p.index_id IN (0,1) GROUP BY t.name HAVING SUM(p.rows)>0 ORDER BY t.name
-- ===== q2.sql
##10_columns
SELECT TABLE_CATALOG, TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM u8cloud.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME IN ('bd_corp','bd_glorg','bd_glbook','bd_glorgbook','bd_accsubj','bd_subjscheme','bd_subjtype','bd_subjass','bd_bdinfo','bd_vouchertype','gl_voucher','gl_detail','gl_freevalue','bd_cashflow','gl_cashflowcase','gl_transfer','gl_transferdetail','gl_syssettled','bd_accperiod','bd_accperiodmonth','gl_dealclass','iufo_report','iufo_measure','iufo_keyword','iufo_key_comb','gl_initbuild','gl_reconcileunit','gl_contrastclass','gl_commonvoucher','gl_othervoucher','gl_extendreport_item','gl_extendreport_prop','gl_subrelation','bd_defdoclist','gl_transfersort','gl_balance','iufo_task','iufo_reportdir','iufo_checkresult','gl_verifyobj','bd_accrelation','fi_formulaitem','iufo_function','gl_vouchermode','gl_vchfreevalue','gl_dtlfreevalue') ORDER BY TABLE_NAME, ORDINAL_POSITION
##11_qz_only_tables
SELECT TABLE_NAME FROM u8cloud_QZ.INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('bd_corp','bd_glorg','bd_glbook','bd_glorgbook','bd_accsubj','bd_subjscheme','bd_subjtype','bd_subjass','bd_bdinfo','bd_vouchertype','gl_voucher','gl_detail','gl_freevalue','bd_cashflow','gl_cashflowcase','gl_transfer','gl_transferdetail','gl_syssettled','bd_accperiod','bd_accperiodmonth','gl_dealclass','iufo_report','iufo_measure','iufo_keyword','iufo_key_comb','gl_initbuild','gl_reconcileunit','gl_contrastclass','gl_commonvoucher','gl_othervoucher','gl_extendreport_item','gl_extendreport_prop','gl_subrelation','bd_defdoclist','gl_transfersort','gl_balance','iufo_task','iufo_reportdir','iufo_checkresult','gl_verifyobj','bd_accrelation','fi_formulaitem','iufo_function','gl_vouchermode','gl_vchfreevalue','gl_dtlfreevalue')
-- ===== q3.sql
##QZ_a_corp
SELECT c.pk_corp, c.unitcode, c.unitname, c.unitshortname, f.unitcode, c.ishasaccount, c.isseal, c.isworkingunit, c.begindate FROM u8cloud_QZ.dbo.bd_corp c LEFT JOIN u8cloud_QZ.dbo.bd_corp f ON f.pk_corp=c.fathercorp WHERE c.dr=0 ORDER BY c.unitcode
##QZ_b_books
SELECT ob.glorgbookcode, ob.glorgbookname, o.glorgcode, o.glorgname, o.glorgtype, b.code, b.name, s.subjschemecode, s.subjschemename, s.subjcoderule, s.controllevel, ob.isstartuse, ob.sealflag, ob.type, pm.month, pa.periodyear, (SELECT COUNT(*) FROM u8cloud_QZ.dbo.gl_voucher v WHERE v.pk_glorgbook=ob.pk_glorgbook AND v.dr=0) FROM u8cloud_QZ.dbo.bd_glorgbook ob JOIN u8cloud_QZ.dbo.bd_glorg o ON o.pk_glorg=ob.pk_glorg JOIN u8cloud_QZ.dbo.bd_glbook b ON b.pk_glbook=ob.pk_glbook LEFT JOIN u8cloud_QZ.dbo.bd_subjscheme s ON s.pk_subjscheme=b.pk_subjscheme LEFT JOIN u8cloud_QZ.dbo.bd_accperiodmonth pm ON pm.pk_accperiodmonth=ob.pk_startaccperiod LEFT JOIN u8cloud_QZ.dbo.bd_accperiod pa ON pa.pk_accperiod=pm.pk_accperiod WHERE ob.dr=0 ORDER BY ob.glorgbookcode
##QZ_c_subjscheme
SELECT subjschemecode, subjschemename, subjcoderule, controllevel, (SELECT COUNT(*) FROM u8cloud_QZ.dbo.bd_accsubj a WHERE a.pk_subjscheme=s.pk_subjscheme AND a.dr=0) FROM u8cloud_QZ.dbo.bd_subjscheme s WHERE dr=0
##QZ_d_subjtype
SELECT t.subjtypename, t.balanorient, t.typeproperty, t.accsystem, COUNT(a.pk_accsubj) FROM u8cloud_QZ.dbo.bd_subjtype t LEFT JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_subjtype=t.pk_subjtype AND a.dr=0 WHERE t.dr=0 GROUP BY t.subjtypename, t.balanorient, t.typeproperty, t.accsystem ORDER BY 1
##QZ_e_subj_levels
SELECT ob.glorgbookcode, a.subjlev, COUNT(*), SUM(CASE WHEN a.endflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN a.sealflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN a.cashbankflag IS NOT NULL AND a.cashbankflag<>0 THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.bd_accsubj a LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=a.pk_glorgbook WHERE a.dr=0 GROUP BY ob.glorgbookcode, a.subjlev ORDER BY 1,2
##QZ_f_subj_l12
SELECT a.subjcode, MAX(a.subjname), a.subjlev, MAX(a.balanorient), MAX(t.subjtypename), MAX(CAST(a.cashbankflag AS VARCHAR)), COUNT(DISTINCT a.pk_glorgbook), MAX(a.endflag), MAX(a.sealflag) FROM u8cloud_QZ.dbo.bd_accsubj a LEFT JOIN u8cloud_QZ.dbo.bd_subjtype t ON t.pk_subjtype=a.pk_subjtype WHERE a.dr=0 AND a.subjlev<=2 GROUP BY a.subjcode, a.subjlev ORDER BY a.subjcode
##QZ_g_subjass
SELECT LEFT(a.subjcode,4), i.bdname, COUNT(*), SUM(CASE WHEN s.isempty='Y' THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.bd_subjass s JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_accsubj=s.pk_accsubj JOIN u8cloud_QZ.dbo.bd_bdinfo i ON i.pk_bdinfo=s.pk_bdinfo WHERE s.dr=0 AND a.dr=0 GROUP BY LEFT(a.subjcode,4), i.bdname ORDER BY 1,2
##QZ_h_bdinfo_used
SELECT i.bdcode, i.bdname, i.bdtype, i.tablename, COUNT(*) FROM u8cloud_QZ.dbo.bd_subjass s JOIN u8cloud_QZ.dbo.bd_bdinfo i ON i.pk_bdinfo=s.pk_bdinfo WHERE s.dr=0 GROUP BY i.bdcode, i.bdname, i.bdtype, i.tablename ORDER BY 5 DESC
##QZ_i_vouchertype
SELECT vouchtype, vouchtypename, forshort, restrictflag, sealflag, pk_corp, pk_glorgbook FROM u8cloud_QZ.dbo.bd_vouchertype WHERE dr=0
##QZ_j_vch_by_year_sys
SELECT ob.glorgbookcode, v.year, v.pk_system, v.voucherkind, v.discardflag, COUNT(*), COUNT(DISTINCT v.period), COUNT(DISTINCT v.pk_prepared) FROM u8cloud_QZ.dbo.gl_voucher v LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE v.dr=0 GROUP BY ob.glorgbookcode, v.year, v.pk_system, v.voucherkind, v.discardflag ORDER BY 1,2,3
##QZ_k_vch_2025_month
SELECT ob.glorgbookcode, v.year, v.period, v.pk_system, COUNT(*), SUM(CASE WHEN v.signflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN v.pk_checked IS NOT NULL THEN 1 ELSE 0 END), SUM(CASE WHEN v.pk_manager IS NOT NULL THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.gl_voucher v LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE v.dr=0 AND v.year IN ('2025','2026') GROUP BY ob.glorgbookcode, v.year, v.period, v.pk_system ORDER BY 1,2,3,4
##QZ_l_detail_2025
SELECT ob.glorgbookcode, v.pk_system, COUNT(*), COUNT(DISTINCT d.pk_voucher), SUM(CASE WHEN d.assid IS NOT NULL AND d.assid<>'' THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.gl_detail d JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE d.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY ob.glorgbookcode, v.pk_system
##QZ_m_detail_subj_2025
SELECT v.pk_system, LEFT(a.subjcode,4), MAX(a1.subjname), COUNT(*), SUM(CASE WHEN d.assid IS NOT NULL AND d.assid<>'' THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.gl_detail d JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj LEFT JOIN u8cloud_QZ.dbo.bd_accsubj a1 ON a1.subjcode=LEFT(a.subjcode,4) AND a1.pk_glorgbook=a.pk_glorgbook WHERE d.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY v.pk_system, LEFT(a.subjcode,4) ORDER BY 1,2
##QZ_n_aux_usage_2025
SELECT i.bdname, COUNT(*) FROM u8cloud_QZ.dbo.gl_detail d JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud_QZ.dbo.gl_freevalue f ON f.freevalueid=d.assid JOIN u8cloud_QZ.dbo.bd_bdinfo i ON i.pk_bdinfo=f.checktype WHERE d.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY i.bdname ORDER BY 2 DESC
##QZ_o_cashflow_items
SELECT c.cfitemcode, c.cfitemname, c.cftype, c.itemflag, p.cfitemcode, c.isseal, c.pk_corp FROM u8cloud_QZ.dbo.bd_cashflow c LEFT JOIN u8cloud_QZ.dbo.bd_cashflow p ON p.pk_cashflow=c.fathernode WHERE c.dr=0 ORDER BY c.pk_corp, c.cfitemcode
##QZ_p_cashflow_case_year
SELECT v.year, v.pk_system, COUNT(*), COUNT(DISTINCT cf.pk_detail), COUNT(DISTINCT c.cfitemcode) FROM u8cloud_QZ.dbo.gl_cashflowcase cf JOIN u8cloud_QZ.dbo.gl_detail d ON d.pk_detail=cf.pk_detail JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud_QZ.dbo.bd_cashflow c ON c.pk_cashflow=cf.pk_cashflow WHERE cf.dr=0 AND v.dr=0 GROUP BY v.year, v.pk_system ORDER BY 1,2
##QZ_q_cash_lines_tagged_2025
SELECT v.pk_system, COUNT(*), SUM(CASE WHEN EXISTS (SELECT 1 FROM u8cloud_QZ.dbo.gl_cashflowcase cf WHERE cf.pk_detail=d.pk_detail AND cf.dr=0) THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.gl_detail d JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj WHERE d.dr=0 AND v.dr=0 AND v.year='2025' AND a.cashbankflag IS NOT NULL AND a.cashbankflag<>0 GROUP BY v.pk_system
##QZ_r_cashflow_item_use_2025
SELECT c.cfitemcode, MAX(c.cfitemname), COUNT(*) FROM u8cloud_QZ.dbo.gl_cashflowcase cf JOIN u8cloud_QZ.dbo.bd_cashflow c ON c.pk_cashflow=cf.pk_cashflow JOIN u8cloud_QZ.dbo.gl_detail d ON d.pk_detail=cf.pk_detail JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher WHERE cf.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY c.cfitemcode ORDER BY 1
##QZ_s_subrelation
SELECT ad.subjcode, ac.subjcode, c.cfitemcode, r.aspect, r.iscd, r.isdd, COUNT(*) FROM u8cloud_QZ.dbo.gl_subrelation r LEFT JOIN u8cloud_QZ.dbo.bd_accsubj ad ON ad.pk_accsubj=r.pk_debitsubject LEFT JOIN u8cloud_QZ.dbo.bd_accsubj ac ON ac.pk_accsubj=r.pk_creditsubject LEFT JOIN u8cloud_QZ.dbo.bd_cashflow c ON c.pk_cashflow=r.pk_cashflow WHERE r.dr=0 GROUP BY ad.subjcode, ac.subjcode, c.cfitemcode, r.aspect, r.iscd, r.isdd ORDER BY 7 DESC
##QZ_t_transfer
SELECT ob.glorgbookcode, s.transcode, s.transname, t.transferno, t.note, t.transfertype, t.cycle, t.transfrequency, (SELECT COUNT(*) FROM u8cloud_QZ.dbo.gl_transferhistory h WHERE h.pk_transfer=t.pk_transfer) FROM u8cloud_QZ.dbo.gl_transfer t LEFT JOIN u8cloud_QZ.dbo.gl_transfersort s ON s.pk_transsort=t.pk_transsort LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=t.pk_glorgbook WHERE t.dr=0 ORDER BY 1,4
##QZ_u_transferdetail
SELECT ob.glorgbookcode, t.transferno, td.detailorder, td.orientation, a.subjcode, a.subjname, LEFT(td.amountformula,300), td.ass FROM u8cloud_QZ.dbo.gl_transferdetail td JOIN u8cloud_QZ.dbo.gl_transfer t ON t.pk_transfer=td.pk_transfer LEFT JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_accsubj=td.pk_accsubj LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=t.pk_glorgbook WHERE td.dr=0 AND t.dr=0 ORDER BY 1,2,3
##QZ_v_settled
SELECT ob.glorgbookcode, s.funccode, s.settledyear, s.settledperiod, s.settleddate FROM u8cloud_QZ.dbo.gl_syssettled s LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=s.pk_glorgbook WHERE s.dr=0 ORDER BY 1,2
##QZ_w_periods
SELECT p.periodyear, COUNT(m.pk_accperiodmonth), MIN(m.begindate), MAX(m.enddate), SUM(CASE WHEN m.isadj='Y' THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.bd_accperiod p LEFT JOIN u8cloud_QZ.dbo.bd_accperiodmonth m ON m.pk_accperiod=p.pk_accperiod AND m.dr=0 WHERE p.dr=0 GROUP BY p.periodyear ORDER BY 1
##QZ_x_initbuild
SELECT ob.glorgbookcode, i.builtyear FROM u8cloud_QZ.dbo.gl_initbuild i LEFT JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=i.pk_glorgbook WHERE i.dr=0
##QZ_y_iufo_reports
SELECT r.reportcode, r.name, r.report_type, r.ismodel, r.is_builtin, d.dir_name, DATALENGTH(r.repinfo), (SELECT COUNT(*) FROM u8cloud_QZ.dbo.iufo_measure m WHERE m.reportpk=r.id), r.modify_time FROM u8cloud_QZ.dbo.iufo_report r LEFT JOIN u8cloud_QZ.dbo.iufo_reportdir d ON d.dir_id=r.directoryid WHERE r.dr=0 ORDER BY r.reportcode
##QZ_z_iufo_tasks
SELECT t.name, t.state, t.sealflag, (SELECT COUNT(*) FROM u8cloud_QZ.dbo.iufo_checkresult c WHERE c.taskid=t.id) FROM u8cloud_QZ.dbo.iufo_task t WHERE t.dr=0
##QZ_za_iufo_keywords
SELECT code, name, type, isbuiltin, time_div FROM u8cloud_QZ.dbo.iufo_keyword WHERE dr=0
##QZ_zc_dap_vouchtemp
SELECT dap.systypecode, COUNT(*) FROM u8cloud_QZ.dbo.dap_vouchtemp dap GROUP BY dap.systypecode
##QZ_zd_dealclass
SELECT classno, classname, moduleid FROM u8cloud_QZ.dbo.gl_dealclass WHERE dr=0
##QZ_ze_verifyobj
SELECT a.subjcode, a.subjname, v.bverifybyass, v.userflag FROM u8cloud_QZ.dbo.gl_verifyobj v LEFT JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_accsubj=v.pk_accsubj WHERE v.dr=0
##QZ_zf_explanation_gl_2025
SELECT LEFT(v.explanation,24), COUNT(*) FROM u8cloud_QZ.dbo.gl_voucher v WHERE v.dr=0 AND v.year='2025' AND v.pk_system='GL' GROUP BY LEFT(v.explanation,24) HAVING COUNT(*)>=6 ORDER BY 2 DESC
##QZ_zg_explanation_xx_2025
SELECT LEFT(v.explanation,12), COUNT(*) FROM u8cloud_QZ.dbo.gl_voucher v WHERE v.dr=0 AND v.year='2025' AND v.pk_system NOT IN ('GL') GROUP BY LEFT(v.explanation,12) HAVING COUNT(*)>=20 ORDER BY 2 DESC
##QZ_zi_commonvoucher
SELECT LEFT(commoncode,20), LEFT(explanation,30), pk_system FROM u8cloud_QZ.dbo.gl_commonvoucher WHERE dr=0
##QZ_zj_iufo_dirs
SELECT dir_name, dir_type, ismodel, is_builtin FROM u8cloud_QZ.dbo.iufo_reportdir WHERE dr=0
##SF_a_corp
SELECT c.pk_corp, c.unitcode, c.unitname, c.unitshortname, f.unitcode, c.ishasaccount, c.isseal, c.isworkingunit, c.begindate FROM u8cloud.dbo.bd_corp c LEFT JOIN u8cloud.dbo.bd_corp f ON f.pk_corp=c.fathercorp WHERE c.dr=0 ORDER BY c.unitcode
##SF_b_books
SELECT ob.glorgbookcode, ob.glorgbookname, o.glorgcode, o.glorgname, o.glorgtype, b.code, b.name, s.subjschemecode, s.subjschemename, s.subjcoderule, s.controllevel, ob.isstartuse, ob.sealflag, ob.type, pm.month, pa.periodyear, (SELECT COUNT(*) FROM u8cloud.dbo.gl_voucher v WHERE v.pk_glorgbook=ob.pk_glorgbook AND v.dr=0) FROM u8cloud.dbo.bd_glorgbook ob JOIN u8cloud.dbo.bd_glorg o ON o.pk_glorg=ob.pk_glorg JOIN u8cloud.dbo.bd_glbook b ON b.pk_glbook=ob.pk_glbook LEFT JOIN u8cloud.dbo.bd_subjscheme s ON s.pk_subjscheme=b.pk_subjscheme LEFT JOIN u8cloud.dbo.bd_accperiodmonth pm ON pm.pk_accperiodmonth=ob.pk_startaccperiod LEFT JOIN u8cloud.dbo.bd_accperiod pa ON pa.pk_accperiod=pm.pk_accperiod WHERE ob.dr=0 ORDER BY ob.glorgbookcode
##SF_c_subjscheme
SELECT subjschemecode, subjschemename, subjcoderule, controllevel, (SELECT COUNT(*) FROM u8cloud.dbo.bd_accsubj a WHERE a.pk_subjscheme=s.pk_subjscheme AND a.dr=0) FROM u8cloud.dbo.bd_subjscheme s WHERE dr=0
##SF_d_subjtype
SELECT t.subjtypename, t.balanorient, t.typeproperty, t.accsystem, COUNT(a.pk_accsubj) FROM u8cloud.dbo.bd_subjtype t LEFT JOIN u8cloud.dbo.bd_accsubj a ON a.pk_subjtype=t.pk_subjtype AND a.dr=0 WHERE t.dr=0 GROUP BY t.subjtypename, t.balanorient, t.typeproperty, t.accsystem ORDER BY 1
##SF_e_subj_levels
SELECT ob.glorgbookcode, a.subjlev, COUNT(*), SUM(CASE WHEN a.endflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN a.sealflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN a.cashbankflag IS NOT NULL AND a.cashbankflag<>0 THEN 1 ELSE 0 END) FROM u8cloud.dbo.bd_accsubj a LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=a.pk_glorgbook WHERE a.dr=0 GROUP BY ob.glorgbookcode, a.subjlev ORDER BY 1,2
##SF_f_subj_l12
SELECT a.subjcode, MAX(a.subjname), a.subjlev, MAX(a.balanorient), MAX(t.subjtypename), MAX(CAST(a.cashbankflag AS VARCHAR)), COUNT(DISTINCT a.pk_glorgbook), MAX(a.endflag), MAX(a.sealflag) FROM u8cloud.dbo.bd_accsubj a LEFT JOIN u8cloud.dbo.bd_subjtype t ON t.pk_subjtype=a.pk_subjtype WHERE a.dr=0 AND a.subjlev<=2 GROUP BY a.subjcode, a.subjlev ORDER BY a.subjcode
##SF_g_subjass
SELECT LEFT(a.subjcode,4), i.bdname, COUNT(*), SUM(CASE WHEN s.isempty='Y' THEN 1 ELSE 0 END) FROM u8cloud.dbo.bd_subjass s JOIN u8cloud.dbo.bd_accsubj a ON a.pk_accsubj=s.pk_accsubj JOIN u8cloud.dbo.bd_bdinfo i ON i.pk_bdinfo=s.pk_bdinfo WHERE s.dr=0 AND a.dr=0 GROUP BY LEFT(a.subjcode,4), i.bdname ORDER BY 1,2
##SF_h_bdinfo_used
SELECT i.bdcode, i.bdname, i.bdtype, i.tablename, COUNT(*) FROM u8cloud.dbo.bd_subjass s JOIN u8cloud.dbo.bd_bdinfo i ON i.pk_bdinfo=s.pk_bdinfo WHERE s.dr=0 GROUP BY i.bdcode, i.bdname, i.bdtype, i.tablename ORDER BY 5 DESC
##SF_i_vouchertype
SELECT vouchtype, vouchtypename, forshort, restrictflag, sealflag, pk_corp, pk_glorgbook FROM u8cloud.dbo.bd_vouchertype WHERE dr=0
##SF_j_vch_by_year_sys
SELECT ob.glorgbookcode, v.year, v.pk_system, v.voucherkind, v.discardflag, COUNT(*), COUNT(DISTINCT v.period), COUNT(DISTINCT v.pk_prepared) FROM u8cloud.dbo.gl_voucher v LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE v.dr=0 GROUP BY ob.glorgbookcode, v.year, v.pk_system, v.voucherkind, v.discardflag ORDER BY 1,2,3
##SF_k_vch_2025_month
SELECT ob.glorgbookcode, v.year, v.period, v.pk_system, COUNT(*), SUM(CASE WHEN v.signflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN v.pk_checked IS NOT NULL THEN 1 ELSE 0 END), SUM(CASE WHEN v.pk_manager IS NOT NULL THEN 1 ELSE 0 END) FROM u8cloud.dbo.gl_voucher v LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE v.dr=0 AND v.year IN ('2025','2026') GROUP BY ob.glorgbookcode, v.year, v.period, v.pk_system ORDER BY 1,2,3,4
##SF_l_detail_2025
SELECT ob.glorgbookcode, v.pk_system, COUNT(*), COUNT(DISTINCT d.pk_voucher), SUM(CASE WHEN d.assid IS NOT NULL AND d.assid<>'' THEN 1 ELSE 0 END) FROM u8cloud.dbo.gl_detail d JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE d.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY ob.glorgbookcode, v.pk_system
##SF_m_detail_subj_2025
SELECT v.pk_system, LEFT(a.subjcode,4), MAX(a1.subjname), COUNT(*), SUM(CASE WHEN d.assid IS NOT NULL AND d.assid<>'' THEN 1 ELSE 0 END) FROM u8cloud.dbo.gl_detail d JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj LEFT JOIN u8cloud.dbo.bd_accsubj a1 ON a1.subjcode=LEFT(a.subjcode,4) AND a1.pk_glorgbook=a.pk_glorgbook WHERE d.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY v.pk_system, LEFT(a.subjcode,4) ORDER BY 1,2
##SF_n_aux_usage_2025
SELECT i.bdname, COUNT(*) FROM u8cloud.dbo.gl_detail d JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud.dbo.gl_freevalue f ON f.freevalueid=d.assid JOIN u8cloud.dbo.bd_bdinfo i ON i.pk_bdinfo=f.checktype WHERE d.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY i.bdname ORDER BY 2 DESC
##SF_o_cashflow_items
SELECT c.cfitemcode, c.cfitemname, c.cftype, c.itemflag, p.cfitemcode, c.isseal, c.pk_corp FROM u8cloud.dbo.bd_cashflow c LEFT JOIN u8cloud.dbo.bd_cashflow p ON p.pk_cashflow=c.fathernode WHERE c.dr=0 ORDER BY c.pk_corp, c.cfitemcode
##SF_p_cashflow_case_year
SELECT v.year, v.pk_system, COUNT(*), COUNT(DISTINCT cf.pk_detail), COUNT(DISTINCT c.cfitemcode) FROM u8cloud.dbo.gl_cashflowcase cf JOIN u8cloud.dbo.gl_detail d ON d.pk_detail=cf.pk_detail JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud.dbo.bd_cashflow c ON c.pk_cashflow=cf.pk_cashflow WHERE cf.dr=0 AND v.dr=0 GROUP BY v.year, v.pk_system ORDER BY 1,2
##SF_q_cash_lines_tagged_2025
SELECT v.pk_system, COUNT(*), SUM(CASE WHEN EXISTS (SELECT 1 FROM u8cloud.dbo.gl_cashflowcase cf WHERE cf.pk_detail=d.pk_detail AND cf.dr=0) THEN 1 ELSE 0 END) FROM u8cloud.dbo.gl_detail d JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj WHERE d.dr=0 AND v.dr=0 AND v.year='2025' AND a.cashbankflag IS NOT NULL AND a.cashbankflag<>0 GROUP BY v.pk_system
##SF_r_cashflow_item_use_2025
SELECT c.cfitemcode, MAX(c.cfitemname), COUNT(*) FROM u8cloud.dbo.gl_cashflowcase cf JOIN u8cloud.dbo.bd_cashflow c ON c.pk_cashflow=cf.pk_cashflow JOIN u8cloud.dbo.gl_detail d ON d.pk_detail=cf.pk_detail JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher WHERE cf.dr=0 AND v.dr=0 AND v.year='2025' GROUP BY c.cfitemcode ORDER BY 1
##SF_s_subrelation
SELECT ad.subjcode, ac.subjcode, c.cfitemcode, r.aspect, r.iscd, r.isdd, COUNT(*) FROM u8cloud.dbo.gl_subrelation r LEFT JOIN u8cloud.dbo.bd_accsubj ad ON ad.pk_accsubj=r.pk_debitsubject LEFT JOIN u8cloud.dbo.bd_accsubj ac ON ac.pk_accsubj=r.pk_creditsubject LEFT JOIN u8cloud.dbo.bd_cashflow c ON c.pk_cashflow=r.pk_cashflow WHERE r.dr=0 GROUP BY ad.subjcode, ac.subjcode, c.cfitemcode, r.aspect, r.iscd, r.isdd ORDER BY 7 DESC
##SF_t_transfer
SELECT ob.glorgbookcode, s.transcode, s.transname, t.transferno, t.note, t.transfertype, t.cycle, t.transfrequency, (SELECT COUNT(*) FROM u8cloud.dbo.gl_transferhistory h WHERE h.pk_transfer=t.pk_transfer) FROM u8cloud.dbo.gl_transfer t LEFT JOIN u8cloud.dbo.gl_transfersort s ON s.pk_transsort=t.pk_transsort LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=t.pk_glorgbook WHERE t.dr=0 ORDER BY 1,4
##SF_u_transferdetail
SELECT ob.glorgbookcode, t.transferno, td.detailorder, td.orientation, a.subjcode, a.subjname, LEFT(td.amountformula,300), td.ass FROM u8cloud.dbo.gl_transferdetail td JOIN u8cloud.dbo.gl_transfer t ON t.pk_transfer=td.pk_transfer LEFT JOIN u8cloud.dbo.bd_accsubj a ON a.pk_accsubj=td.pk_accsubj LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=t.pk_glorgbook WHERE td.dr=0 AND t.dr=0 ORDER BY 1,2,3
##SF_v_settled
SELECT ob.glorgbookcode, s.funccode, s.settledyear, s.settledperiod, s.settleddate FROM u8cloud.dbo.gl_syssettled s LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=s.pk_glorgbook WHERE s.dr=0 ORDER BY 1,2
##SF_w_periods
SELECT p.periodyear, COUNT(m.pk_accperiodmonth), MIN(m.begindate), MAX(m.enddate), SUM(CASE WHEN m.isadj='Y' THEN 1 ELSE 0 END) FROM u8cloud.dbo.bd_accperiod p LEFT JOIN u8cloud.dbo.bd_accperiodmonth m ON m.pk_accperiod=p.pk_accperiod AND m.dr=0 WHERE p.dr=0 GROUP BY p.periodyear ORDER BY 1
##SF_x_initbuild
SELECT ob.glorgbookcode, i.builtyear FROM u8cloud.dbo.gl_initbuild i LEFT JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=i.pk_glorgbook WHERE i.dr=0
##SF_y_iufo_reports
SELECT r.reportcode, r.name, r.report_type, r.ismodel, r.is_builtin, d.dir_name, DATALENGTH(r.repinfo), (SELECT COUNT(*) FROM u8cloud.dbo.iufo_measure m WHERE m.reportpk=r.id), r.modify_time FROM u8cloud.dbo.iufo_report r LEFT JOIN u8cloud.dbo.iufo_reportdir d ON d.dir_id=r.directoryid WHERE r.dr=0 ORDER BY r.reportcode
##SF_z_iufo_tasks
SELECT t.name, t.state, t.sealflag, (SELECT COUNT(*) FROM u8cloud.dbo.iufo_checkresult c WHERE c.taskid=t.id) FROM u8cloud.dbo.iufo_task t WHERE t.dr=0
##SF_za_iufo_keywords
SELECT code, name, type, isbuiltin, time_div FROM u8cloud.dbo.iufo_keyword WHERE dr=0
##SF_zc_dap_vouchtemp
SELECT dap.systypecode, COUNT(*) FROM u8cloud.dbo.dap_vouchtemp dap GROUP BY dap.systypecode
##SF_zd_dealclass
SELECT classno, classname, moduleid FROM u8cloud.dbo.gl_dealclass WHERE dr=0
##SF_ze_verifyobj
SELECT a.subjcode, a.subjname, v.bverifybyass, v.userflag FROM u8cloud.dbo.gl_verifyobj v LEFT JOIN u8cloud.dbo.bd_accsubj a ON a.pk_accsubj=v.pk_accsubj WHERE v.dr=0
##SF_zf_explanation_gl_2025
SELECT LEFT(v.explanation,24), COUNT(*) FROM u8cloud.dbo.gl_voucher v WHERE v.dr=0 AND v.year='2025' AND v.pk_system='GL' GROUP BY LEFT(v.explanation,24) HAVING COUNT(*)>=6 ORDER BY 2 DESC
##SF_zg_explanation_xx_2025
SELECT LEFT(v.explanation,12), COUNT(*) FROM u8cloud.dbo.gl_voucher v WHERE v.dr=0 AND v.year='2025' AND v.pk_system NOT IN ('GL') GROUP BY LEFT(v.explanation,12) HAVING COUNT(*)>=20 ORDER BY 2 DESC
##SF_zi_commonvoucher
SELECT LEFT(commoncode,20), LEFT(explanation,30), pk_system FROM u8cloud.dbo.gl_commonvoucher WHERE dr=0
##SF_zj_iufo_dirs
SELECT dir_name, dir_type, ismodel, is_builtin FROM u8cloud.dbo.iufo_reportdir WHERE dr=0
-- ===== q4.sql
##QZ_a_subj_books
SELECT ob.glorgbookcode, LEFT(a.subjcode,1), a.subjlev, COUNT(*), SUM(CASE WHEN a.endflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN a.sealflag='Y' THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.bd_accsubj a JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=a.pk_glorgbook WHERE ISNULL(a.dr,0)=0 AND ob.glorgbookcode IN ('1001-0001','1003-0001') GROUP BY ob.glorgbookcode, LEFT(a.subjcode,1), a.subjlev ORDER BY 1,2,3
##QZ_b_chart
SELECT a.subjcode, a.subjname, a.subjlev, a.balanorient, t.subjtypename, CAST(a.cashbankflag AS VARCHAR), a.endflag, a.sealflag, STUFF((SELECT ','+i.bdname FROM u8cloud_QZ.dbo.bd_subjass s JOIN u8cloud_QZ.dbo.bd_bdinfo i ON i.pk_bdinfo=s.pk_bdinfo WHERE s.pk_accsubj=a.pk_accsubj AND ISNULL(s.dr,0)=0 FOR XML PATH('')),1,1,'') FROM u8cloud_QZ.dbo.bd_accsubj a JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=a.pk_glorgbook LEFT JOIN u8cloud_QZ.dbo.bd_subjtype t ON t.pk_subjtype=a.pk_subjtype WHERE ISNULL(a.dr,0)=0 AND ob.glorgbookcode='1001-0001' AND a.subjlev<=3 AND ISNULL(a.sealflag,'N')<>'Y' ORDER BY a.subjcode
##QZ_c_dual_2025
SELECT v.pk_system, x.kind, COUNT(*) FROM (SELECT d.pk_voucher, CASE WHEN MAX(CASE WHEN a.subjcode LIKE '[A-E]%' THEN 1 ELSE 0 END)=1 AND MAX(CASE WHEN a.subjcode LIKE '[0-9]%' THEN 1 ELSE 0 END)=1 THEN 'both' WHEN MAX(CASE WHEN a.subjcode LIKE '[A-E]%' THEN 1 ELSE 0 END)=1 THEN 'Q_only' ELSE 'num_only' END kind FROM u8cloud_QZ.dbo.gl_detail d JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj WHERE ISNULL(d.dr,0)=0 AND d.yearv='2025' GROUP BY d.pk_voucher) x JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=x.pk_voucher WHERE ISNULL(v.dr,0)=0 GROUP BY v.pk_system, x.kind ORDER BY 1,2
##QZ_d_cash_tagged_2025
SELECT v.pk_system, CAST(a.cashbankflag AS VARCHAR), COUNT(*), SUM(CASE WHEN cf.pk_detail IS NULL THEN 0 ELSE 1 END) FROM u8cloud_QZ.dbo.gl_detail d JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud_QZ.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj LEFT JOIN (SELECT DISTINCT pk_detail FROM u8cloud_QZ.dbo.gl_cashflowcase WHERE ISNULL(dr,0)=0) cf ON cf.pk_detail=d.pk_detail WHERE ISNULL(d.dr,0)=0 AND ISNULL(v.dr,0)=0 AND v.year='2025' GROUP BY v.pk_system, CAST(a.cashbankflag AS VARCHAR) ORDER BY 1,2
##QZ_e_cols
SELECT TABLE_NAME, COLUMN_NAME FROM u8cloud_QZ.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME IN ('dap_vouchtemp','dap_insubjclass','xx_exsystem','xx_xlog','iufo_measure_data_8x5cs8tw','IUFO_CO1629332591') ORDER BY TABLE_NAME, ORDINAL_POSITION
##QZ_f_dr_null
SELECT 'accsubj', COUNT(*), SUM(CASE WHEN dr IS NULL THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.bd_accsubj UNION ALL SELECT 'voucher', COUNT(*), SUM(CASE WHEN dr IS NULL THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.gl_voucher UNION ALL SELECT 'detail', COUNT(*), SUM(CASE WHEN dr IS NULL THEN 1 ELSE 0 END) FROM u8cloud_QZ.dbo.gl_detail
##SF_a_subj_books
SELECT ob.glorgbookcode, LEFT(a.subjcode,1), a.subjlev, COUNT(*), SUM(CASE WHEN a.endflag='Y' THEN 1 ELSE 0 END), SUM(CASE WHEN a.sealflag='Y' THEN 1 ELSE 0 END) FROM u8cloud.dbo.bd_accsubj a JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=a.pk_glorgbook WHERE ISNULL(a.dr,0)=0 AND ob.glorgbookcode IN ('1001-0001','1003-0001') GROUP BY ob.glorgbookcode, LEFT(a.subjcode,1), a.subjlev ORDER BY 1,2,3
##SF_b_chart
SELECT a.subjcode, a.subjname, a.subjlev, a.balanorient, t.subjtypename, CAST(a.cashbankflag AS VARCHAR), a.endflag, a.sealflag, STUFF((SELECT ','+i.bdname FROM u8cloud.dbo.bd_subjass s JOIN u8cloud.dbo.bd_bdinfo i ON i.pk_bdinfo=s.pk_bdinfo WHERE s.pk_accsubj=a.pk_accsubj AND ISNULL(s.dr,0)=0 FOR XML PATH('')),1,1,'') FROM u8cloud.dbo.bd_accsubj a JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=a.pk_glorgbook LEFT JOIN u8cloud.dbo.bd_subjtype t ON t.pk_subjtype=a.pk_subjtype WHERE ISNULL(a.dr,0)=0 AND ob.glorgbookcode='1001-0001' AND a.subjlev<=3 AND ISNULL(a.sealflag,'N')<>'Y' ORDER BY a.subjcode
##SF_c_dual_2025
SELECT v.pk_system, x.kind, COUNT(*) FROM (SELECT d.pk_voucher, CASE WHEN MAX(CASE WHEN a.subjcode LIKE '[A-E]%' THEN 1 ELSE 0 END)=1 AND MAX(CASE WHEN a.subjcode LIKE '[0-9]%' THEN 1 ELSE 0 END)=1 THEN 'both' WHEN MAX(CASE WHEN a.subjcode LIKE '[A-E]%' THEN 1 ELSE 0 END)=1 THEN 'Q_only' ELSE 'num_only' END kind FROM u8cloud.dbo.gl_detail d JOIN u8cloud.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj WHERE ISNULL(d.dr,0)=0 AND d.yearv='2025' GROUP BY d.pk_voucher) x JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=x.pk_voucher WHERE ISNULL(v.dr,0)=0 GROUP BY v.pk_system, x.kind ORDER BY 1,2
##SF_d_cash_tagged_2025
SELECT v.pk_system, CAST(a.cashbankflag AS VARCHAR), COUNT(*), SUM(CASE WHEN cf.pk_detail IS NULL THEN 0 ELSE 1 END) FROM u8cloud.dbo.gl_detail d JOIN u8cloud.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher JOIN u8cloud.dbo.bd_accsubj a ON a.pk_accsubj=d.pk_accsubj LEFT JOIN (SELECT DISTINCT pk_detail FROM u8cloud.dbo.gl_cashflowcase WHERE ISNULL(dr,0)=0) cf ON cf.pk_detail=d.pk_detail WHERE ISNULL(d.dr,0)=0 AND ISNULL(v.dr,0)=0 AND v.year='2025' GROUP BY v.pk_system, CAST(a.cashbankflag AS VARCHAR) ORDER BY 1,2
##SF_e_cols
SELECT TABLE_NAME, COLUMN_NAME FROM u8cloud.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME IN ('dap_vouchtemp','dap_insubjclass','xx_exsystem','xx_xlog','iufo_measure_data_8x5cs8tw','IUFO_CO1629332591') ORDER BY TABLE_NAME, ORDINAL_POSITION
##SF_f_dr_null
SELECT 'accsubj', COUNT(*), SUM(CASE WHEN dr IS NULL THEN 1 ELSE 0 END) FROM u8cloud.dbo.bd_accsubj UNION ALL SELECT 'voucher', COUNT(*), SUM(CASE WHEN dr IS NULL THEN 1 ELSE 0 END) FROM u8cloud.dbo.gl_voucher UNION ALL SELECT 'detail', COUNT(*), SUM(CASE WHEN dr IS NULL THEN 1 ELSE 0 END) FROM u8cloud.dbo.gl_detail
-- ===== q5.sql
##QZ_a_repinfo_head
SELECT reportcode, DATALENGTH(repinfo), CONVERT(VARCHAR(40), CAST(SUBSTRING(repinfo,1,16) AS VARBINARY(16)), 2), CHARINDEX(CAST('QMYE' AS VARBINARY(8)), CAST(repinfo AS VARBINARY(MAX))), CHARINDEX(CAST('FS(' AS VARBINARY(8)), CAST(repinfo AS VARBINARY(MAX))) FROM u8cloud_QZ.dbo.iufo_report WHERE dr=0 AND reportcode IN ('会企01表','会企02表','会企03表','K会01','K会02','K会03','2019会企01表')
##QZ_b_exsystem
SELECT exsystemcode, exsystemname, pfxxrule FROM u8cloud_QZ.dbo.xx_exsystem WHERE ISNULL(dr,0)=0
##QZ_c_vouchtemp
SELECT pk_sys, LEFT(businame,30), vouchtype, COUNT(*) FROM u8cloud_QZ.dbo.dap_vouchtemp WHERE ISNULL(dr,0)=0 GROUP BY pk_sys, LEFT(businame,30), vouchtype ORDER BY 4 DESC
##QZ_d_insubjclass
SELECT LEFT(insubjclasscode,20), LEFT(insubjclassname,30), defaultsubjcode, pk_billtype, COUNT(*) FROM u8cloud_QZ.dbo.dap_insubjclass WHERE ISNULL(dr,0)=0 GROUP BY LEFT(insubjclasscode,20), LEFT(insubjclassname,30), defaultsubjcode, pk_billtype ORDER BY 1
##QZ_e_xlog_src
SELECT doc_source, doc_destination, doc_type, LEFT(doc_dscpt,20), COUNT(*), MIN(LEFT(doc_time,7)), MAX(LEFT(doc_time,7)) FROM u8cloud_QZ.dbo.xx_xlog WHERE ISNULL(dr,0)=0 GROUP BY doc_source, doc_destination, doc_type, LEFT(doc_dscpt,20) ORDER BY 5 DESC
##QZ_g_xx_prepared_2025
SELECT ob.glorgbookcode, v.pk_system, COUNT(DISTINCT v.pk_prepared), COUNT(*) FROM u8cloud_QZ.dbo.gl_voucher v JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE v.dr=0 AND v.year='2025' AND ob.glorgbookcode IN ('1001-0001') GROUP BY ob.glorgbookcode, v.pk_system
##SF_a_repinfo_head
SELECT reportcode, DATALENGTH(repinfo), CONVERT(VARCHAR(40), CAST(SUBSTRING(repinfo,1,16) AS VARBINARY(16)), 2), CHARINDEX(CAST('QMYE' AS VARBINARY(8)), CAST(repinfo AS VARBINARY(MAX))), CHARINDEX(CAST('FS(' AS VARBINARY(8)), CAST(repinfo AS VARBINARY(MAX))) FROM u8cloud.dbo.iufo_report WHERE dr=0 AND reportcode IN ('会企01表','会企02表','会企03表','K会01','K会02','K会03','2019会企01表')
##SF_b_exsystem
SELECT exsystemcode, exsystemname, pfxxrule FROM u8cloud.dbo.xx_exsystem WHERE ISNULL(dr,0)=0
##SF_c_vouchtemp
SELECT pk_sys, LEFT(businame,30), vouchtype, COUNT(*) FROM u8cloud.dbo.dap_vouchtemp WHERE ISNULL(dr,0)=0 GROUP BY pk_sys, LEFT(businame,30), vouchtype ORDER BY 4 DESC
##SF_d_insubjclass
SELECT LEFT(insubjclasscode,20), LEFT(insubjclassname,30), defaultsubjcode, pk_billtype, COUNT(*) FROM u8cloud.dbo.dap_insubjclass WHERE ISNULL(dr,0)=0 GROUP BY LEFT(insubjclasscode,20), LEFT(insubjclassname,30), defaultsubjcode, pk_billtype ORDER BY 1
##SF_e_xlog_src
SELECT doc_source, doc_destination, doc_type, LEFT(doc_dscpt,20), COUNT(*), MIN(LEFT(doc_time,7)), MAX(LEFT(doc_time,7)) FROM u8cloud.dbo.xx_xlog WHERE ISNULL(dr,0)=0 GROUP BY doc_source, doc_destination, doc_type, LEFT(doc_dscpt,20) ORDER BY 5 DESC
##SF_g_xx_prepared_2025
SELECT ob.glorgbookcode, v.pk_system, COUNT(DISTINCT v.pk_prepared), COUNT(*) FROM u8cloud.dbo.gl_voucher v JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook WHERE v.dr=0 AND v.year='2025' AND ob.glorgbookcode IN ('1001-0001') GROUP BY ob.glorgbookcode, v.pk_system
-- ===== q6.sql
##QZ_rep_01
SELECT reportcode, CONVERT(VARCHAR(MAX), CAST(repinfo AS VARBINARY(MAX)), 2) FROM u8cloud_QZ.dbo.iufo_report WHERE dr=0 AND reportcode=N'会企01表'
##QZ_rep_02
SELECT reportcode, CONVERT(VARCHAR(MAX), CAST(repinfo AS VARBINARY(MAX)), 2) FROM u8cloud_QZ.dbo.iufo_report WHERE dr=0 AND reportcode=N'会企02表'
##QZ_rep_03
SELECT reportcode, CONVERT(VARCHAR(MAX), CAST(repinfo AS VARBINARY(MAX)), 2) FROM u8cloud_QZ.dbo.iufo_report WHERE dr=0 AND reportcode=N'会企03表'
##SF_rep_03
SELECT reportcode, CONVERT(VARCHAR(MAX), CAST(repinfo AS VARBINARY(MAX)), 2) FROM u8cloud.dbo.iufo_report WHERE dr=0 AND reportcode=N'K会03'
-- ===== q7.sql
##QZ_cat_2025
SELECT ob.glorgbookcode, UPPER(RTRIM(v.pk_system)), CASE WHEN u.user_code='tbadmin' THEN 'tbadmin' ELSE 'other' END, CASE WHEN v.explanation LIKE N'发票%' THEN N'发票制证' WHEN v.explanation LIKE N'PSA%' THEN N'PSA推送' WHEN v.explanation LIKE N'%损益%' THEN N'结转损益' WHEN v.explanation LIKE N'%成本结转%' OR v.explanation LIKE N'%研发支出%' THEN N'成本/研发结转' WHEN v.explanation LIKE N'%折旧%' OR v.explanation LIKE N'%摊销%' THEN N'折旧摊销' WHEN v.explanation LIKE N'%工资%' OR v.explanation LIKE N'%奖金%' THEN N'工资' WHEN v.explanation LIKE N'%社保%' OR v.explanation LIKE N'%公积金%' OR v.explanation LIKE N'%个税%' THEN N'社保公积金个税' WHEN v.explanation LIKE N'%税%' THEN N'税费' WHEN v.explanation LIKE N'%手续费%' OR v.explanation LIKE N'%利息%' THEN N'银行手续费利息' WHEN v.explanation LIKE N'%回款%' OR v.explanation LIKE N'%收款%' OR v.explanation LIKE N'%进账%' THEN N'回款' WHEN v.explanation LIKE N'%付款%' OR v.explanation LIKE N'%支付%' OR v.explanation LIKE N'%划款%' THEN N'付款' WHEN v.explanation LIKE N'%报销%' OR v.explanation LIKE N'%借款%' OR v.explanation LIKE N'%还款%' THEN N'报销借还款' WHEN v.explanation LIKE N'%计提%' THEN N'其他计提' WHEN v.explanation LIKE N'%理财%' OR v.explanation LIKE N'%基金%' THEN N'理财' ELSE N'其他' END, COUNT(*) FROM u8cloud_QZ.dbo.gl_voucher v JOIN u8cloud_QZ.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook LEFT JOIN u8cloud_QZ.dbo.sm_user u ON u.cuserid=v.pk_prepared WHERE v.dr=0 AND v.year='2025' GROUP BY ob.glorgbookcode, UPPER(RTRIM(v.pk_system)), CASE WHEN u.user_code='tbadmin' THEN 'tbadmin' ELSE 'other' END, CASE WHEN v.explanation LIKE N'发票%' THEN N'发票制证' WHEN v.explanation LIKE N'PSA%' THEN N'PSA推送' WHEN v.explanation LIKE N'%损益%' THEN N'结转损益' WHEN v.explanation LIKE N'%成本结转%' OR v.explanation LIKE N'%研发支出%' THEN N'成本/研发结转' WHEN v.explanation LIKE N'%折旧%' OR v.explanation LIKE N'%摊销%' THEN N'折旧摊销' WHEN v.explanation LIKE N'%工资%' OR v.explanation LIKE N'%奖金%' THEN N'工资' WHEN v.explanation LIKE N'%社保%' OR v.explanation LIKE N'%公积金%' OR v.explanation LIKE N'%个税%' THEN N'社保公积金个税' WHEN v.explanation LIKE N'%税%' THEN N'税费' WHEN v.explanation LIKE N'%手续费%' OR v.explanation LIKE N'%利息%' THEN N'银行手续费利息' WHEN v.explanation LIKE N'%回款%' OR v.explanation LIKE N'%收款%' OR v.explanation LIKE N'%进账%' THEN N'回款' WHEN v.explanation LIKE N'%付款%' OR v.explanation LIKE N'%支付%' OR v.explanation LIKE N'%划款%' THEN N'付款' WHEN v.explanation LIKE N'%报销%' OR v.explanation LIKE N'%借款%' OR v.explanation LIKE N'%还款%' THEN N'报销借还款' WHEN v.explanation LIKE N'%计提%' THEN N'其他计提' WHEN v.explanation LIKE N'%理财%' OR v.explanation LIKE N'%基金%' THEN N'理财' ELSE N'其他' END ORDER BY 1,2,3,5 DESC
##SF_cat_2025
SELECT ob.glorgbookcode, UPPER(RTRIM(v.pk_system)), CASE WHEN u.user_code='tbadmin' THEN 'tbadmin' ELSE 'other' END, CASE WHEN v.explanation LIKE N'发票%' THEN N'发票制证' WHEN v.explanation LIKE N'PSA%' THEN N'PSA推送' WHEN v.explanation LIKE N'%损益%' THEN N'结转损益' WHEN v.explanation LIKE N'%成本结转%' OR v.explanation LIKE N'%研发支出%' THEN N'成本/研发结转' WHEN v.explanation LIKE N'%折旧%' OR v.explanation LIKE N'%摊销%' THEN N'折旧摊销' WHEN v.explanation LIKE N'%工资%' OR v.explanation LIKE N'%奖金%' THEN N'工资' WHEN v.explanation LIKE N'%社保%' OR v.explanation LIKE N'%公积金%' OR v.explanation LIKE N'%个税%' THEN N'社保公积金个税' WHEN v.explanation LIKE N'%税%' THEN N'税费' WHEN v.explanation LIKE N'%手续费%' OR v.explanation LIKE N'%利息%' THEN N'银行手续费利息' WHEN v.explanation LIKE N'%回款%' OR v.explanation LIKE N'%收款%' OR v.explanation LIKE N'%进账%' THEN N'回款' WHEN v.explanation LIKE N'%付款%' OR v.explanation LIKE N'%支付%' OR v.explanation LIKE N'%划款%' THEN N'付款' WHEN v.explanation LIKE N'%报销%' OR v.explanation LIKE N'%借款%' OR v.explanation LIKE N'%还款%' THEN N'报销借还款' WHEN v.explanation LIKE N'%计提%' THEN N'其他计提' WHEN v.explanation LIKE N'%理财%' OR v.explanation LIKE N'%基金%' THEN N'理财' ELSE N'其他' END, COUNT(*) FROM u8cloud.dbo.gl_voucher v JOIN u8cloud.dbo.bd_glorgbook ob ON ob.pk_glorgbook=v.pk_glorgbook LEFT JOIN u8cloud.dbo.sm_user u ON u.cuserid=v.pk_prepared WHERE v.dr=0 AND v.year='2025' GROUP BY ob.glorgbookcode, UPPER(RTRIM(v.pk_system)), CASE WHEN u.user_code='tbadmin' THEN 'tbadmin' ELSE 'other' END, CASE WHEN v.explanation LIKE N'发票%' THEN N'发票制证' WHEN v.explanation LIKE N'PSA%' THEN N'PSA推送' WHEN v.explanation LIKE N'%损益%' THEN N'结转损益' WHEN v.explanation LIKE N'%成本结转%' OR v.explanation LIKE N'%研发支出%' THEN N'成本/研发结转' WHEN v.explanation LIKE N'%折旧%' OR v.explanation LIKE N'%摊销%' THEN N'折旧摊销' WHEN v.explanation LIKE N'%工资%' OR v.explanation LIKE N'%奖金%' THEN N'工资' WHEN v.explanation LIKE N'%社保%' OR v.explanation LIKE N'%公积金%' OR v.explanation LIKE N'%个税%' THEN N'社保公积金个税' WHEN v.explanation LIKE N'%税%' THEN N'税费' WHEN v.explanation LIKE N'%手续费%' OR v.explanation LIKE N'%利息%' THEN N'银行手续费利息' WHEN v.explanation LIKE N'%回款%' OR v.explanation LIKE N'%收款%' OR v.explanation LIKE N'%进账%' THEN N'回款' WHEN v.explanation LIKE N'%付款%' OR v.explanation LIKE N'%支付%' OR v.explanation LIKE N'%划款%' THEN N'付款' WHEN v.explanation LIKE N'%报销%' OR v.explanation LIKE N'%借款%' OR v.explanation LIKE N'%还款%' THEN N'报销借还款' WHEN v.explanation LIKE N'%计提%' THEN N'其他计提' WHEN v.explanation LIKE N'%理财%' OR v.explanation LIKE N'%基金%' THEN N'理财' ELSE N'其他' END ORDER BY 1,2,3,5 DESC
-- ===== q8.sql
##QZ_xx_other_prefix
SELECT LEFT(v.explanation,6), COUNT(*) FROM u8cloud_QZ.dbo.gl_voucher v WHERE v.dr=0 AND v.year='2025' AND v.pk_system='XX' GROUP BY LEFT(v.explanation,6) HAVING COUNT(*)>=40 ORDER BY 2 DESC
##QZ_xx_other_suffix
SELECT RIGHT(RTRIM(v.explanation),6), COUNT(*) FROM u8cloud_QZ.dbo.gl_voucher v WHERE v.dr=0 AND v.year='2025' AND v.pk_system='XX' GROUP BY RIGHT(RTRIM(v.explanation),6) HAVING COUNT(*)>=40 ORDER BY 2 DESC
##QZ_xx_lines_per_voucher
SELECT CASE WHEN n<=2 THEN '1-2' WHEN n<=4 THEN '3-4' WHEN n<=10 THEN '5-10' WHEN n<=50 THEN '11-50' ELSE '50+' END, COUNT(*) FROM (SELECT d.pk_voucher, COUNT(*) n FROM u8cloud_QZ.dbo.gl_detail d JOIN u8cloud_QZ.dbo.gl_voucher v ON v.pk_voucher=d.pk_voucher WHERE v.dr=0 AND d.dr=0 AND v.year='2025' AND v.pk_system='XX' GROUP BY d.pk_voucher) x GROUP BY CASE WHEN n<=2 THEN '1-2' WHEN n<=4 THEN '3-4' WHEN n<=10 THEN '5-10' WHEN n<=50 THEN '11-50' ELSE '50+' END
