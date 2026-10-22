# 验收测试编号缺口

由 architecture/panorama/scan_acceptance_tests.py 生成，勿手改。

仅核对源码中的编号引用；不代表测试已运行或业务规则已验证。

## 有测试无编号（835 条）

### backend/src/test/java/com/forcitis/one/acceptance/ApprovalIdContractTest.java

- `com.forcitis.one.acceptance.ApprovalIdContractTest#eventCatalogueDeclaresTheApprovalIdsAsUuid` — `backend/src/test/java/com/forcitis/one/acceptance/ApprovalIdContractTest.java:151`
- `com.forcitis.one.acceptance.ApprovalIdContractTest#generatedValidatorsTreatTheIdsAsStrings` — `backend/src/test/java/com/forcitis/one/acceptance/ApprovalIdContractTest.java:193`
- `com.forcitis.one.acceptance.ApprovalIdContractTest#noOtherApprovalIdIsLeftInAnotherShape` — `backend/src/test/java/com/forcitis/one/acceptance/ApprovalIdContractTest.java:105`
- `com.forcitis.one.acceptance.ApprovalIdContractTest#taskIdPathParametersAreStringUuid` — `backend/src/test/java/com/forcitis/one/acceptance/ApprovalIdContractTest.java:64`
- `com.forcitis.one.acceptance.ApprovalIdContractTest#taskInstanceAndDocumentIdPropertiesAreStringUuid` — `backend/src/test/java/com/forcitis/one/acceptance/ApprovalIdContractTest.java:89`
- `com.forcitis.one.acceptance.ApprovalIdContractTest#theGuardOnlyLetsTheInboxTaskIdThrough` — `backend/src/test/java/com/forcitis/one/acceptance/ApprovalIdContractTest.java:129`

### backend/src/test/java/com/forcitis/one/acceptance/AuditIdContractTest.java

- `com.forcitis.one.acceptance.AuditIdContractTest#generatedValidatorsDoNotTreatThemAsNumbers` — `backend/src/test/java/com/forcitis/one/acceptance/AuditIdContractTest.java:111`
- `com.forcitis.one.acceptance.AuditIdContractTest#theAuditEntryNamesItsSubjectInTwoFields` — `backend/src/test/java/com/forcitis/one/acceptance/AuditIdContractTest.java:56`
- `com.forcitis.one.acceptance.AuditIdContractTest#theAuditQueriesFilterBySubjectKindAndId` — `backend/src/test/java/com/forcitis/one/acceptance/AuditIdContractTest.java:75`
- `com.forcitis.one.acceptance.AuditIdContractTest#theGuardKnowsTheNewFields` — `backend/src/test/java/com/forcitis/one/acceptance/AuditIdContractTest.java:133`
- `com.forcitis.one.acceptance.AuditIdContractTest#thePropertiesEndingInByAreUuids` — `backend/src/test/java/com/forcitis/one/acceptance/AuditIdContractTest.java:92`

### backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java

- `com.forcitis.one.acceptance.EmployeeIdContractTest#attributionEventsNameTheirSubjectInTwoFields` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:167`
- `com.forcitis.one.acceptance.EmployeeIdContractTest#eventCatalogueDeclaresOwnIdsAsUuid` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:153`
- `com.forcitis.one.acceptance.EmployeeIdContractTest#generatedValidatorsTakeTheIdsAsStrings` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:206`
- `com.forcitis.one.acceptance.EmployeeIdContractTest#noEmployeeOrOrgUnitIdIsANumber` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:116`
- `com.forcitis.one.acceptance.EmployeeIdContractTest#otherIdNamesKeepOnlyTheTwoRuledOnes` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:144`
- `com.forcitis.one.acceptance.EmployeeIdContractTest#ownIdPathParametersAreStringUuid` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:88`
- `com.forcitis.one.acceptance.EmployeeIdContractTest#ownIdPropertiesAreStringUuid` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:72`
- `com.forcitis.one.acceptance.EmployeeIdContractTest#theAttributionResponseKeepsItsFieldNames` — `backend/src/test/java/com/forcitis/one/acceptance/EmployeeIdContractTest.java:196`

### backend/src/test/java/com/forcitis/one/acceptance/EventEnvelopeContractTest.java

- `com.forcitis.one.acceptance.EventEnvelopeContractTest#everyAggregateMapsToAnAuditSubjectKind` — `backend/src/test/java/com/forcitis/one/acceptance/EventEnvelopeContractTest.java:147`
- `com.forcitis.one.acceptance.EventEnvelopeContractTest#everyTypedIdOfTheCatalogueIsAUuid` — `backend/src/test/java/com/forcitis/one/acceptance/EventEnvelopeContractTest.java:106`
- `com.forcitis.one.acceptance.EventEnvelopeContractTest#theCatalogueEnvelopeIsTheFinalShape` — `backend/src/test/java/com/forcitis/one/acceptance/EventEnvelopeContractTest.java:81`
- `com.forcitis.one.acceptance.EventEnvelopeContractTest#theEnvelopeHasNoSource` — `backend/src/test/java/com/forcitis/one/acceptance/EventEnvelopeContractTest.java:54`
- `com.forcitis.one.acceptance.EventEnvelopeContractTest#theGeneratorAndTheFieldListDropSource` — `backend/src/test/java/com/forcitis/one/acceptance/EventEnvelopeContractTest.java:95`
- `com.forcitis.one.acceptance.EventEnvelopeContractTest#theIdsOfTheEnvelopeAreUuids` — `backend/src/test/java/com/forcitis/one/acceptance/EventEnvelopeContractTest.java:45`

### backend/src/test/java/com/forcitis/one/acceptance/PrimaryKeyExemptionsTest.java

- `com.forcitis.one.acceptance.PrimaryKeyExemptionsTest#onlyTheFiveFrameworkTablesAreExempt` — `backend/src/test/java/com/forcitis/one/acceptance/PrimaryKeyExemptionsTest.java:32`
- `com.forcitis.one.acceptance.PrimaryKeyExemptionsTest#theBaselineIsGone` — `backend/src/test/java/com/forcitis/one/acceptance/PrimaryKeyExemptionsTest.java:26`

### backend/src/test/java/com/forcitis/one/acceptance/ScalarIdContractTest.java

- `com.forcitis.one.acceptance.ScalarIdContractTest#catalogueDescribesIdsAsScalars` — `backend/src/test/java/com/forcitis/one/acceptance/ScalarIdContractTest.java:92`
- `com.forcitis.one.acceptance.ScalarIdContractTest#openApiHasNoValueWrapper` — `backend/src/test/java/com/forcitis/one/acceptance/ScalarIdContractTest.java:105`

### backend/src/test/java/com/forcitis/one/acceptance/SingleColumnPrimaryKeysIT.java

- `com.forcitis.one.acceptance.SingleColumnPrimaryKeysIT#theThreeTablesHaveASingleUuidKeyAndTheirOldKeyIsUnique` — `backend/src/test/java/com/forcitis/one/acceptance/SingleColumnPrimaryKeysIT.java:65`

### backend/src/test/java/com/forcitis/one/acceptance/SourceTypeBoundaryTest.java

- `com.forcitis.one.acceptance.SourceTypeBoundaryTest#anExceptionCoversOneFileAndOnlyTheSourceTypeRule` — `backend/src/test/java/com/forcitis/one/acceptance/SourceTypeBoundaryTest.java:265`
- `com.forcitis.one.acceptance.SourceTypeBoundaryTest#counterExamplesAreLeftAlone` — `backend/src/test/java/com/forcitis/one/acceptance/SourceTypeBoundaryTest.java:248`
- `com.forcitis.one.acceptance.SourceTypeBoundaryTest#injectedViolationsAreCaught` — `backend/src/test/java/com/forcitis/one/acceptance/SourceTypeBoundaryTest.java:224`
- `com.forcitis.one.acceptance.SourceTypeBoundaryTest#noSourceOrLegacyIdOutsideIntegration` — `backend/src/test/java/com/forcitis/one/acceptance/SourceTypeBoundaryTest.java:192`
- `com.forcitis.one.acceptance.SourceTypeBoundaryTest#noSourceTypeOutsideIntegrationBeyondTheList` — `backend/src/test/java/com/forcitis/one/acceptance/SourceTypeBoundaryTest.java:180`
- `com.forcitis.one.acceptance.SourceTypeBoundaryTest#theExceptionListIsWellFormedAndNoLineIsStale` — `backend/src/test/java/com/forcitis/one/acceptance/SourceTypeBoundaryTest.java:201`

### backend/src/test/java/com/forcitis/one/acceptance/UserIdContractTest.java

- `com.forcitis.one.acceptance.UserIdContractTest#eventCatalogueDeclaresUserIdsAsUuid` — `backend/src/test/java/com/forcitis/one/acceptance/UserIdContractTest.java:105`
- `com.forcitis.one.acceptance.UserIdContractTest#userIdParametersAreStringUuid` — `backend/src/test/java/com/forcitis/one/acceptance/UserIdContractTest.java:78`
- `com.forcitis.one.acceptance.UserIdContractTest#userIdPropertiesAreStringUuid` — `backend/src/test/java/com/forcitis/one/acceptance/UserIdContractTest.java:61`

### backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetApprovalWithdrawnRenameIT.java

- `com.forcitis.one.acceptance.cost.timesheet.TimesheetApprovalWithdrawnRenameIT#aCancelledSubmissionIsPublishedAsTimesheetApprovalWithdrawn` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetApprovalWithdrawnRenameIT.java:77`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetApprovalWithdrawnRenameIT#theCataloguesUseOnlyTheNewName` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetApprovalWithdrawnRenameIT.java:68`

### backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java

- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#aDuplicateKeyOfAnUnknownConstraintIsNotAConcurrentChange` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:243`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#aTimesheetNumberClashIsStillAConcurrentChange` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:222`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#aWeekendDayAllowsOneTimesheetPerEmployeeLikeAnyOtherDay` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:181`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#anyExistingStatusRefusesANewTimesheetForTheSameDayBeforeNumbering` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:155`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#concurrentCreationOfTheSameEmployeeDayLeavesOneRowAndOneNumber` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:100`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#theTableCarriesTheEmployeeDayConstraint` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:197`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#whenThePrecheckIsBypassedTheUniqueConstraintStillRefusesAsWorkDateDuplicate` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:140`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetEmployeeDayUniqueIT#workDateIsACalendarDayAndTodayIsTheShanghaiDay` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetEmployeeDayUniqueIT.java:270`

### backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetNumberingRuleVersionIT.java

- `com.forcitis.one.acceptance.cost.timesheet.TimesheetNumberingRuleVersionIT#aMigratedRowWithoutARuleVersionCanStillBeRead` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetNumberingRuleVersionIT.java:71`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetNumberingRuleVersionIT#aNewTimesheetKeepsTheRuleVersionItsNumberWasIssuedUnder` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetNumberingRuleVersionIT.java:50`

### backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetOwnerAccountsIT.java

- `com.forcitis.one.acceptance.cost.timesheet.TimesheetOwnerAccountsIT#aNewTimesheetIsFiledUnderTheSignedInEmployee` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetOwnerAccountsIT.java:151`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetOwnerAccountsIT#onlyTheOwnerActsOnATimesheet` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetOwnerAccountsIT.java:124`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetOwnerAccountsIT#theCurrentUsersLegalEntityIsTheirEmployeesAttribution` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetOwnerAccountsIT.java:165`

### backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRejectedResubmitIT.java

- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRejectedResubmitIT#aRejectedTimesheetIsRevisedAndSubmittedAgainAsTheNextRound` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRejectedResubmitIT.java:146`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRejectedResubmitIT#anApprovedTimesheetCannotBeSubmittedAgain` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRejectedResubmitIT.java:180`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRejectedResubmitIT#cancellingTheApprovalTurnsASubmittedTimesheetBackIntoADraft` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRejectedResubmitIT.java:198`

### backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java

- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#aMigratedTimesheetWithoutARuleVersionCanBeInsertedButOneWithAVersionCannot` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:355`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#aNewNonDraftTimesheetWithRoundZeroIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:319`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#approvalResultsDoNotTouchRoundOrSubmittedDate` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:236`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#columnCommentsMatchTheImplementation` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:402`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#draftsInBothShapesCanBeInserted` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:344`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#firstSubmissionIsRoundOneSubmittedToday` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:161`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#resubmissionAfterRejectionIsTheNextRoundWithANewSubmittedDate` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:193`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#resubmissionAfterReturnIsRoundTwoWithANewSubmittedDate` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:174`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#roundAndSubmittedDateMustBeZeroAndEmptyTogether` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:329`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#theTwoConstraintsExistWithTheAgreedDefinitions` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:381`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetRoundSubmittedCheckIT#withdrawingToDraftKeepsRoundAndSubmittedDateAndResubmissionAddsOne` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetRoundSubmittedCheckIT.java:213`

### backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetStatusConsistencyTest.java

- `com.forcitis.one.acceptance.cost.timesheet.TimesheetStatusConsistencyTest#checkEnumMachineAndCatalogueAgreeOnTheStatuses` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetStatusConsistencyTest.java:67`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetStatusConsistencyTest#rejectedGoesBackToSubmittedAndApprovedStaysTheOnlyEnd` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetStatusConsistencyTest.java:93`
- `com.forcitis.one.acceptance.cost.timesheet.TimesheetStatusConsistencyTest#rejectedIsEditableLikeDraftAndReturned` — `backend/src/test/java/com/forcitis/one/acceptance/cost/timesheet/TimesheetStatusConsistencyTest.java:84`

### backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT.java

- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarMakeUpWeekendIT#constraintDefinitionInCatalog` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT.java:173`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarMakeUpWeekendIT#holidaysOnWeekdaysAndWeekends` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT.java:125`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarMakeUpWeekendIT#makeUpWorkdayOnAWeekdayIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT.java:115`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarMakeUpWeekendIT#makeUpWorkdayOnSaturdayAndSunday` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT.java:102`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarMakeUpWeekendIT#sameVerdictInAnySessionTimeZone` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT.java:152`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarMakeUpWeekendIT#wednesdayMakeUpDayOverHttpIsA422` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarMakeUpWeekendIT.java:137`

### backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarYearsPagingIT.java

- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarYearsPagingIT#bothPagesAreCutInTheDatabase` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarYearsPagingIT.java:117`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarYearsPagingIT#lastPageEndsExactlyAtTheBoundary` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarYearsPagingIT.java:96`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarYearsPagingIT#limitTakesEffect` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarYearsPagingIT.java:110`
- `com.forcitis.one.acceptance.organization.attendance.WorkCalendarYearsPagingIT#walkingPagesOfThreeCoversEveryYearOnce` — `backend/src/test/java/com/forcitis/one/acceptance/organization/attendance/WorkCalendarYearsPagingIT.java:81`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/AttributionSubjectColumnsIT.java

- `com.forcitis.one.acceptance.organization.workforce.AttributionSubjectColumnsIT#aCorrectionNamesExactlyOneExistingSubject` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/AttributionSubjectColumnsIT.java:122`
- `com.forcitis.one.acceptance.organization.workforce.AttributionSubjectColumnsIT#aSubjectHasOneAttributionAtMost` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/AttributionSubjectColumnsIT.java:112`
- `com.forcitis.one.acceptance.organization.workforce.AttributionSubjectColumnsIT#anAttributionNamesExactlyOneSubject` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/AttributionSubjectColumnsIT.java:95`
- `com.forcitis.one.acceptance.organization.workforce.AttributionSubjectColumnsIT#anAttributionPointsAtAnExistingSubject` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/AttributionSubjectColumnsIT.java:105`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/EmployeeCodeNotKeyIT.java

- `com.forcitis.one.acceptance.organization.workforce.EmployeeCodeNotKeyIT#nothingFetchesByTheEmployeeCode` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/EmployeeCodeNotKeyIT.java:85`
- `com.forcitis.one.acceptance.organization.workforce.EmployeeCodeNotKeyIT#theEmployeeCodeIsInNoKey` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/EmployeeCodeNotKeyIT.java:43`
- `com.forcitis.one.acceptance.organization.workforce.EmployeeCodeNotKeyIT#theMethodCheckCatchesALookup` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/EmployeeCodeNotKeyIT.java:101`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/HeldSeparationReleaseIT.java

- `com.forcitis.one.acceptance.organization.workforce.HeldSeparationReleaseIT#aLeaverCannotLiftTheHoldOnTheirOwnSeparation` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/HeldSeparationReleaseIT.java:84`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/LegalEntityAccountingCodeMovedIT.java

- `com.forcitis.one.acceptance.organization.workforce.LegalEntityAccountingCodeMovedIT#contractsFollow` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/LegalEntityAccountingCodeMovedIT.java:70`
- `com.forcitis.one.acceptance.organization.workforce.LegalEntityAccountingCodeMovedIT#theLegalEntityResponseHasNoAccountingCode` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/LegalEntityAccountingCodeMovedIT.java:57`
- `com.forcitis.one.acceptance.organization.workforce.LegalEntityAccountingCodeMovedIT#theLegalEntityTableHasNoAccountingCode` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/LegalEntityAccountingCodeMovedIT.java:38`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/LegalEntityCodeCorrectionIT.java

- `com.forcitis.one.acceptance.organization.workforce.LegalEntityCodeCorrectionIT#codeIsCorrectableUntilANumberCarriesIt` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/LegalEntityCodeCorrectionIT.java:70`
- `com.forcitis.one.acceptance.organization.workforce.LegalEntityCodeCorrectionIT#onlyTheNumberedLegalEntityIsLocked` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/LegalEntityCodeCorrectionIT.java:98`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnAttributionIT.java

- `com.forcitis.one.acceptance.organization.workforce.OwnAttributionIT#nobodyChangesTheirOwnAttribution` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnAttributionIT.java:43`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnIdColumnsIT.java

- `com.forcitis.one.acceptance.organization.workforce.OwnIdColumnsIT#everyOwnIdColumnIsUuid` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnIdColumnsIT.java:135`
- `com.forcitis.one.acceptance.organization.workforce.OwnIdColumnsIT#theEightTablesHaveAOneColumnUuidKeyUnderTheirOldName` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnIdColumnsIT.java:151`
- `com.forcitis.one.acceptance.organization.workforce.OwnIdColumnsIT#theForeignKeyIndexesExist` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnIdColumnsIT.java:184`
- `com.forcitis.one.acceptance.organization.workforce.OwnIdColumnsIT#theHeldSeparationsHaveASingleUuidKeyAndAUniquePair` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnIdColumnsIT.java:212`
- `com.forcitis.one.acceptance.organization.workforce.OwnIdColumnsIT#theNaturalKeysStayUnique` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/OwnIdColumnsIT.java:168`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceLengthLimitsIT.java

- `com.forcitis.one.acceptance.organization.workforce.WorkforceLengthLimitsIT#wf11CorrectionReasonColumnLimit` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceLengthLimitsIT.java:118`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceLengthLimitsIT#wf11CorrectionReasonOverTheEntryLimitIsRefusedBeforeAnyWrite` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceLengthLimitsIT.java:151`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceLengthLimitsIT#wf11OrgUnitVersionNameAndCodeLimit` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceLengthLimitsIT.java:103`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceLengthLimitsIT#wf11TextColumnTakesExactlyItsLimit` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceLengthLimitsIT.java:96`

### backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java

- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf04LegalEntityVersionCodeFormat` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:110`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf05RevokedAtPairsWithRevokedStatus` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:127`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf05UpdatesKeepTheSamePairing` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:140`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf09AssignmentVersionPosition` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:189`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf09AssignmentVersionReportsTo` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:198`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf09OrgUnitVersionHead` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:174`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf09OrgUnitVersionParent` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:167`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf09OrgUnitVersionRegionalReceivablesManager` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:181`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf09VersionForeignKeysAreDeferred` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:205`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf11BlankTextIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:265`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf11LegalEntityNameLength` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:296`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf11LegalEntityNameNotBlank` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:304`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf11LegalEntityVersionName` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:312`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf11OrgUnitVersionNameAndCode` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:277`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf12EmployeeCorrectionNeedsRoot` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:355`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf12EmployeePeriodNeedsRoot` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:335`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf12EmptyCorrectionIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:364`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf12OrgUnitCorrectionNeedsRoot` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:346`
- `com.forcitis.one.acceptance.organization.workforce.WorkforceRowConstraintsIT#wf12OrgUnitPeriodNeedsRoot` — `backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/WorkforceRowConstraintsIT.java:324`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/AggregateIdsMadeAtConstructionIT.java

- `com.forcitis.one.acceptance.platform.approval.AggregateIdsMadeAtConstructionIT#theIdsAreMadeBeforeTheRepositorySeesTheAggregate` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/AggregateIdsMadeAtConstructionIT.java:76`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalAccountsIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalAccountsIT#anEmployeeWithoutAnAccountCannotBeAssigned` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalAccountsIT.java:131`
- `com.forcitis.one.acceptance.platform.approval.ApprovalAccountsIT#onlyTheCandidateActsOnTheTask` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalAccountsIT.java:150`
- `com.forcitis.one.acceptance.platform.approval.ApprovalAccountsIT#theInitiatorNeverApprovesTheirOwnTimesheet` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalAccountsIT.java:113`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalEventRenameIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalEventRenameIT#aHandledTaskIsPublishedAsTaskHandled` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalEventRenameIT.java:61`
- `com.forcitis.one.acceptance.platform.approval.ApprovalEventRenameIT#anOverdueTaskIsPublishedAsApprovalTaskBecameOverdue` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalEventRenameIT.java:76`
- `com.forcitis.one.acceptance.platform.approval.ApprovalEventRenameIT#theCataloguesUseOnlyTheNewNames` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalEventRenameIT.java:49`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOrderNotFromIdsIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalOrderNotFromIdsIT#historyFollowsTheRecordNumberNotTheRecordId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOrderNotFromIdsIT.java:72`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOrderNotFromIdsIT#numbersRestartPerRequestAndNeverRepeat` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOrderNotFromIdsIT.java:94`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOrderNotFromIdsIT#tasksAreRestoredByCreationTime` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOrderNotFromIdsIT.java:117`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOrderNotFromIdsIT#theRequestIdIsMadeBeforeTheRepositorySeesIt` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOrderNotFromIdsIT.java:140`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOwnIdColumnsIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalOwnIdColumnsIT#childTablesHangOnTheApprovalRequest` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOwnIdColumnsIT.java:145`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOwnIdColumnsIT#deliveriesAreKeyedByColumns` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOwnIdColumnsIT.java:216`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOwnIdColumnsIT#recordsHaveANumber` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOwnIdColumnsIT.java:201`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOwnIdColumnsIT#theConstraintsAndIndexesExist` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOwnIdColumnsIT.java:169`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOwnIdColumnsIT#theIdColumnsAreUuidAndTheTextKeysAreGone` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOwnIdColumnsIT.java:110`
- `com.forcitis.one.acceptance.platform.approval.ApprovalOwnIdColumnsIT#theKeysAreSingleUuidColumns` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalOwnIdColumnsIT.java:134`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#blankSummaryRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:82`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#everyLegalCombinationAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:137`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#malformedDocumentTypeRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:61`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#onBehalfOfOutsideReassignmentRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:128`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#otherActionWithAssigneeRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:120`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#summaryTooLongRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:75`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#summaryWithinLimitAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:90`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#timesheetRequestAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:69`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRequestTextAndRecordRulesIT#transferWithoutAssigneeRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRequestTextAndRecordRulesIT.java:112`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aFinishedRequestWithoutAReasonIsRefusedByTheDatabase` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:277`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aHandlingRecordNeedsItsReasonInTheDatabase` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:301`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aPendingTaskHasNoHandler` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:358`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aRecordNeedsARealInstance` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:425`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aRecordsTaskBelongsToTheRecordsRequest` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:455`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aRejectedDocumentMayStartAgain` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:263`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aRejectedOrReturnedTaskNeedsItsReasonInTheDatabase` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:315`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aRequestNeedsARealCurrentInstance` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:435`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aRunningRequestWithAReasonAndAnEmptyReasonAreRefusedByTheDatabase` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:288`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#aTaskNeedsARealInstance` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:417`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#anApprovedHashBelongsToABindingApproval` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:386`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#anAutomaticPassIsAnApproval` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:378`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#anOpenTaskCarriesNoCompletionFields` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:405`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#outcomeAndEffectComeInPairs` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:368`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#overdueNeedsADeadlineAndEscalationNeedsOverdue` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:396`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#reassignmentWithoutAReasonIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:215`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#rejectionWithoutAReasonIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:156`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#returnWithoutAReasonIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:178`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#startRoundsCannotBeChanged` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:475`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#startRoundsCannotBeTruncated` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:491`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#theCurrentRoundIsAtLeastOne` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:446`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#theHandlingChainStillWorks` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:339`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#transferWithoutAReasonIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:194`
- `com.forcitis.one.acceptance.platform.approval.ApprovalRowRulesIT#withdrawAndCancelNeedAReasonAndTheDocumentMayStartAgain` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalRowRulesIT.java:236`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalTaskPathIdIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalTaskPathIdIT#aTaskIdThatIsNoCanonicalUuidIsAMalformedRequest` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalTaskPathIdIT.java:51`
- `com.forcitis.one.acceptance.platform.approval.ApprovalTaskPathIdIT#aWellFormedIdIsLookedUp` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalTaskPathIdIT.java:68`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalUniqueKeysIT.java

- `com.forcitis.one.acceptance.platform.approval.ApprovalUniqueKeysIT#aProcessInstanceIsSkippedOnlyOnce` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalUniqueKeysIT.java:91`
- `com.forcitis.one.acceptance.platform.approval.ApprovalUniqueKeysIT#aRequestWithoutACurrentInstanceIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalUniqueKeysIT.java:78`
- `com.forcitis.one.acceptance.platform.approval.ApprovalUniqueKeysIT#aSecondStartRoundCannotUseTheSameInstance` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalUniqueKeysIT.java:54`
- `com.forcitis.one.acceptance.platform.approval.ApprovalUniqueKeysIT#twoRequestsCannotShareTheCurrentInstance` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/ApprovalUniqueKeysIT.java:66`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/AssigneeInputUuidIT.java

- `com.forcitis.one.acceptance.platform.approval.AssigneeInputUuidIT#aCanonicalUuidIsAssigned` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/AssigneeInputUuidIT.java:129`
- `com.forcitis.one.acceptance.platform.approval.AssigneeInputUuidIT#aNonUuidValueBesideAValidOneFailsTheAssignment` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/AssigneeInputUuidIT.java:115`
- `com.forcitis.one.acceptance.platform.approval.AssigneeInputUuidIT#onlyNonUuidValuesFailTheAssignment` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/AssigneeInputUuidIT.java:121`

### backend/src/test/java/com/forcitis/one/acceptance/platform/approval/RetractionInitiatorAccountsIT.java

- `com.forcitis.one.acceptance.platform.approval.RetractionInitiatorAccountsIT#onlyTheInitiatorRetracts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/approval/RetractionInitiatorAccountsIT.java:54`

### backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveAndOutcomeConstraintsIT.java

- `com.forcitis.one.acceptance.platform.audit.AuditArchiveAndOutcomeConstraintsIT#duplicateFirstEntryIdIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveAndOutcomeConstraintsIT.java:103`
- `com.forcitis.one.acceptance.platform.audit.AuditArchiveAndOutcomeConstraintsIT#duplicateObjectKeyIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveAndOutcomeConstraintsIT.java:116`
- `com.forcitis.one.acceptance.platform.audit.AuditArchiveAndOutcomeConstraintsIT#invalidHashIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveAndOutcomeConstraintsIT.java:128`
- `com.forcitis.one.acceptance.platform.audit.AuditArchiveAndOutcomeConstraintsIT#methodWithoutOutcomeIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveAndOutcomeConstraintsIT.java:145`
- `com.forcitis.one.acceptance.platform.audit.AuditArchiveAndOutcomeConstraintsIT#otherCombinationsOfOutcomeAndMethodAreAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveAndOutcomeConstraintsIT.java:157`

### backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveOrderIT.java

- `com.forcitis.one.acceptance.platform.audit.AuditArchiveOrderIT#theArchiveFollowsMomentsNotIds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditArchiveOrderIT.java:110`

### backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectColumnsIT.java

- `com.forcitis.one.acceptance.platform.audit.AuditSubjectColumnsIT#archiveBatchesCarryTheMomentsOfTheirEntries` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectColumnsIT.java:147`
- `com.forcitis.one.acceptance.platform.audit.AuditSubjectColumnsIT#theIdIsEmptyExactlyForTheKindsWithoutAnObject` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectColumnsIT.java:118`
- `com.forcitis.one.acceptance.platform.audit.AuditSubjectColumnsIT#theSubjectIndexIsRenamedAndGetsNewColumns` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectColumnsIT.java:90`
- `com.forcitis.one.acceptance.platform.audit.AuditSubjectColumnsIT#theSubjectIsTwoColumns` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectColumnsIT.java:71`

### backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectFromEventIT.java

- `com.forcitis.one.acceptance.platform.audit.AuditSubjectFromEventIT#anAggregateTypeThatIsNotKnownWritesNothingAndRaisesAnAlert` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectFromEventIT.java:67`
- `com.forcitis.one.acceptance.platform.audit.AuditSubjectFromEventIT#theKindComesFromTheAggregateTypeAndTheIdFromTheEnvelope` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/AuditSubjectFromEventIT.java:56`

### backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java

- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#exportAndListAnswerInTheSameFixedOrder` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:305`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#exportFiltersLikeTheListByTheFourAddedParameters` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:280`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#exportRefusesPagingParameters` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:336`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#exportSendsNoCountStatementToTheDatabase` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:396`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#exportSucceedsAtTheCeilingAndFailsOneRowAbove` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:370`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#invalidFiltersAreRefusedTheSameWayByBoth` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:358`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#ownRecordsAreAnObjectWithItemsAndNextCursor` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:167`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#ownRecordsCursorCannotReachOtherPeoplesRows` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:218`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#ownRecordsPageThroughTiesWithoutGapOrRepeat` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:189`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#ownRecordsRefuseCursorsThatAreNotTheirOwn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:244`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#ownRecordsRefuseLimitsOutsideOneToTwoHundred` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:270`
- `com.forcitis.one.acceptance.platform.audit.K3b4AuditPagingIT#sortIsAnUnknownParameter` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4AuditPagingIT.java:323`

### backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4ContractTest.java

- `com.forcitis.one.acceptance.platform.audit.K3b4ContractTest#bothOperationsDescribeTheFixedOrder` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4ContractTest.java:73`
- `com.forcitis.one.acceptance.platform.audit.K3b4ContractTest#exportAndListDeclareTheSameFilterParameters` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4ContractTest.java:59`
- `com.forcitis.one.acceptance.platform.audit.K3b4ContractTest#ownRecordsResponseIsAPageObject` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4ContractTest.java:113`
- `com.forcitis.one.acceptance.platform.audit.K3b4ContractTest#theConfiguredCeilingIsTheSharedExportMaxAndTheGapsAreClosed` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4ContractTest.java:85`
- `com.forcitis.one.acceptance.platform.audit.K3b4ContractTest#theRetiredErrorCodeIsGoneEverywhere` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/K3b4ContractTest.java:100`

### backend/src/test/java/com/forcitis/one/acceptance/platform/audit/OwnLoginRecordsBySubjectIT.java

- `com.forcitis.one.acceptance.platform.audit.OwnLoginRecordsBySubjectIT#ownRecordsAreFoundByKindAndUserId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/OwnLoginRecordsBySubjectIT.java:42`
- `com.forcitis.one.acceptance.platform.audit.OwnLoginRecordsBySubjectIT#theFieldWhitelistIsUnchanged` — `backend/src/test/java/com/forcitis/one/acceptance/platform/audit/OwnLoginRecordsBySubjectIT.java:66`

### backend/src/test/java/com/forcitis/one/acceptance/platform/eventing/EventEnvelopeActorIT.java

- `com.forcitis.one.acceptance.platform.eventing.EventEnvelopeActorIT#aJobActsAsSystemWithAStableUuid` — `backend/src/test/java/com/forcitis/one/acceptance/platform/eventing/EventEnvelopeActorIT.java:112`
- `com.forcitis.one.acceptance.platform.eventing.EventEnvelopeActorIT#aSignedInPersonActsWithTheirAccountId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/eventing/EventEnvelopeActorIT.java:85`

### backend/src/test/java/com/forcitis/one/acceptance/platform/eventing/EventEnvelopeAggregateIdIT.java

- `com.forcitis.one.acceptance.platform.eventing.EventEnvelopeAggregateIdIT#everyAggregateIdIsACanonicalUuid` — `backend/src/test/java/com/forcitis/one/acceptance/platform/eventing/EventEnvelopeAggregateIdIT.java:94`
- `com.forcitis.one.acceptance.platform.eventing.EventEnvelopeAggregateIdIT#everyAggregateIdIsThePrimaryKeyOfItsAggregate` — `backend/src/test/java/com/forcitis/one/acceptance/platform/eventing/EventEnvelopeAggregateIdIT.java:119`
- `com.forcitis.one.acceptance.platform.eventing.EventEnvelopeAggregateIdIT#theEnvelopeHasNoSource` — `backend/src/test/java/com/forcitis/one/acceptance/platform/eventing/EventEnvelopeAggregateIdIT.java:144`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/AccountEnsureConcurrencyIT.java

- `com.forcitis.one.acceptance.platform.identity.AccountEnsureConcurrencyIT#twoFirstLoginsAtOnceOpenOneAccount` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/AccountEnsureConcurrencyIT.java:38`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/AccountEntriesConcurrencyIT.java

- `com.forcitis.one.acceptance.platform.identity.AccountEntriesConcurrencyIT#fourEntriesAtOnceOpenOneAccount` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/AccountEntriesConcurrencyIT.java:181`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/AuthorizationConfirmerAccountsIT.java

- `com.forcitis.one.acceptance.platform.identity.AuthorizationConfirmerAccountsIT#aHolderOfTheChangedRoleCannotConfirm` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/AuthorizationConfirmerAccountsIT.java:79`
- `com.forcitis.one.acceptance.platform.identity.AuthorizationConfirmerAccountsIT#theRequesterAndTheBeneficiaryCannotConfirm` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/AuthorizationConfirmerAccountsIT.java:110`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/EmergencyLoginEmploymentIT.java

- `com.forcitis.one.acceptance.platform.identity.EmergencyLoginEmploymentIT#aStaleSnapshotWithoutTheBoundEmployeeLetsThroughAndAlerts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/EmergencyLoginEmploymentIT.java:198`
- `com.forcitis.one.acceptance.platform.identity.EmergencyLoginEmploymentIT#aWorkingSnapshotWithTheBoundEmployeeEmployedLetsThrough` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/EmergencyLoginEmploymentIT.java:164`
- `com.forcitis.one.acceptance.platform.identity.EmergencyLoginEmploymentIT#aWorkingSnapshotWithoutTheBoundEmployeeRefuses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/EmergencyLoginEmploymentIT.java:147`
- `com.forcitis.one.acceptance.platform.identity.EmergencyLoginEmploymentIT#noSnapshotAtAllLetsThroughAndAlerts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/EmergencyLoginEmploymentIT.java:177`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java

- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#aValidEmployeeIdOpensOrReusesTheAccount` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:125`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#anEmployeeIdThatIsNoCanonicalUuidIsWrongUsage` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:98`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#anEmployeeThatDoesNotExistIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:114`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#fu1ASeparatedEmployeeStillCounts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:218`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#fu1AWitnessWhoDoesNotExistIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:182`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#fu1AnOperatorWhoDoesNotExistIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:176`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#fu1MissingSameOrNotAnIdIsStillRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:194`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#fu1NeitherExistsIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:188`
- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeIdIT#fu1TwoDifferentExistingEmployeesProceed` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeIdIT.java:203`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeRowOnlyIT.java

- `com.forcitis.one.acceptance.platform.identity.OperationsCommandEmployeeRowOnlyIT#nits1AnOperatorAndWitnessWithoutAssignmentsAreAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OperationsCommandEmployeeRowOnlyIT.java:63`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OwnRolesIT.java

- `com.forcitis.one.acceptance.platform.identity.OwnRolesIT#nobodyChangesTheirOwnRoles` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OwnRolesIT.java:58`
- `com.forcitis.one.acceptance.platform.identity.OwnRolesIT#theAccountPathTakesTheUserIdNotTheEmployeeId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/OwnRolesIT.java:89`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SessionPrincipalIT.java

- `com.forcitis.one.acceptance.platform.identity.SessionPrincipalIT#aPrincipalThatIsNotACanonicalUuidIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SessionPrincipalIT.java:61`
- `com.forcitis.one.acceptance.platform.identity.SessionPrincipalIT#theSessionOfALoginCarriesTheAccountUuid` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SessionPrincipalIT.java:72`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SessionPurgeClockIT.java

- `com.forcitis.one.acceptance.platform.identity.SessionPurgeClockIT#aFreshSessionSurvivesAPurge` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SessionPurgeClockIT.java:66`
- `com.forcitis.one.acceptance.platform.identity.SessionPurgeClockIT#anIdleSessionIsStillPurged` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SessionPurgeClockIT.java:79`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_aBrokenSecondAccountStillAlertsWithTheModeOn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:94`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_boundPersonAccountDisabledAlerts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:71`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_boundPersonLostSuperAdminAlerts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:57`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_noneUsableAlertsWithTheModeOn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:46`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_oneUsableAccountIsEnoughWithTheModeOn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:36`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_oneUsableWithTheModeOnWritesExactlyOneMark` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:204`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_otherCasesWriteNoMark` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:215`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p211_theAccountItselfDisabledAlerts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:82`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p212_noneAlertsAndTwoPassWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:118`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p212_oneUsableAccountStillAlertsWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:107`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p213_alreadyRegisteredPromptFollowsTheMode` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:154`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p213_registrationPromptFollowsTheMode` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:135`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p213_theWarningTextIsKeptWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:239`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p214_bindingStaysRestrictedToSuperAdminHolders` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:167`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEmergencyMinimumIT#p214_enableStillNeedsTheBinding` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEmergencyMinimumIT.java:187`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEndToEndIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEndToEndIT#p222_grantRoleWithoutWitnessEndToEnd` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEndToEndIT.java:83`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorEndToEndIT#p222_theSameCommandWithoutTheKeyIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorEndToEndIT.java:115`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorMarkTransactionIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorMarkTransactionIT#p202_committedTogether` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorMarkTransactionIT.java:85`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorMarkTransactionIT#p202_disableWithoutWitnessIsRefusedWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorMarkTransactionIT.java:197`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorMarkTransactionIT#p202_grantWithoutWitnessIsRefusedWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorMarkTransactionIT.java:164`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorMarkTransactionIT#p202_revokeWithoutWitnessIsRefusedWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorMarkTransactionIT.java:180`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorMarkTransactionIT#p202_rolledBackTogether` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorMarkTransactionIT.java:108`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeFailClosedIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeFailClosedIT#p113_unreachableDatabaseIsRefusedByTheCheckItself` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeFailClosedIT.java:44`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeFailClosedIT#p117_failedAnnouncementAuditRefusesTheStart` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeFailClosedIT.java:62`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeLaunchRetryTest.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeLaunchRetryTest#aTakenPortIsRetriedWithAnotherPort` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeLaunchRetryTest.java:25`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeLaunchRetryTest#everyOtherFailureIsReturnedAsItIs` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeLaunchRetryTest.java:56`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeLaunchRetryTest#retriesAreCapped` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeLaunchRetryTest.java:41`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p101_unsetIsOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:102`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p101_unsetUnderProdIsOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:108`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p102_falseIsOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:119`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p103_trueWithDevIsOn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:129`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p104_notALiteralFails` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:138`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p105_emptyFailsInEverySource` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:152`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p105_everySourceIsRead` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:145`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p105_garbageFailsInEverySource` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:159`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p105_prodRefusesInEverySource` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:166`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p106_activeDevWithProdInTheDefaultProfilesIsOffWhenNotTrue` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:289`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p106_defaultProfileProdWithKeyFails` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:244`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p106_defaultProfileProdWithoutKeyPasses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:251`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p106_prodWithKeyFails` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:185`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p107_activeDevWinsOverProdInTheDefaultProfiles` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:280`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p107_devInTheDefaultProfilesDoesNotEnable` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:273`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p107_trueNeedsDevWithoutProd` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:199`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p108_defaultProfileIdentityOperationsAloneFails` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:258`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p108_defaultProfileIdentityOperationsWithEnvironmentPasses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:266`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p108_identityOperationsNeedsAnEnvironmentProfile` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:206`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p108_identityOperationsWithAnEnvironmentProfilePasses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:215`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeListenerTest#p109_failureNamesKeyAndAdrAndLeaksNothing` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeListenerTest.java:222`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeNotPublishedTest.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeNotPublishedTest#noMainCodeOutsideIdentityNamesTheKey` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeNotPublishedTest.java:102`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeNotPublishedTest#theRepositoryDoesNotPublishTheKey` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeNotPublishedTest.java:78`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeNotPublishedTest#theScanReportsInjectedSamples` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeNotPublishedTest.java:89`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeNotPublishedTest#theSettingsTypeIsPrivateToTheModuleAndNotRefreshable` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeNotPublishedTest.java:128`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeNotPublishedTest#theSourceScanReportsInjectedSamples` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeNotPublishedTest.java:111`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeQueryIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeQueryIT#p110_noRefreshAndNoExposure` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeQueryIT.java:63`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeQueryIT#p110_readOnlyAtStart` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeQueryIT.java:44`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p106_listenerRunsBeforeTheMigrateStep` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:202`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p111_wrongDatabaseRefuses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:53`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p112_sessionUserNotCurrentUser` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:175`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p112_wrongUserRefuses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:60`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p113_modeOffDoesNotCheckTheDatabase` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:78`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p113_rightDatabaseAndUserStarts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:70`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p113_unreachableDatabaseFailsClosed` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:185`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p114_checkComesBeforeTheOperationsCommand` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:90`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p115_checkComesBeforeTheServerAndTheScheduler` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:109`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p116_noWarnLineWhenOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:137`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p116_warnLineWhenOn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:124`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p117_auditEntryPerStartWhenOn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:148`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorModeStartupIT#p117_noAuditEntryWhenOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorModeStartupIT.java:162`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRelaxationRegisterTest#p210_theExistingOperationsTestsStayWhereTheyAre` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java:178`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRelaxationRegisterTest#p220_everyMarkWriterTakesTheEnumNotText` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java:122`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRelaxationRegisterTest#p220_theComparisonReportsAnAddedMissingOrRenamedValue` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java:87`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRelaxationRegisterTest#p220_theEnumHasExactlyTheThreeValuesOfTheTableSoFar` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java:78`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRelaxationRegisterTest#p220_theMarkWriterTakesNoStringAtAll` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java:187`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRelaxationRegisterTest#p221_newActionsAreInTheMustAuditRegister` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java:142`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRelaxationRegisterTest#p221_writersAreRegisteredAgainstTheRowsOfTheContentTable` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRelaxationRegisterTest.java:164`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRevokeRoleIT#p217_revokingAHeldRole` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java:39`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRevokeRoleIT#p218_noAccountIsRefusedAndNoneIsOpened` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java:104`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRevokeRoleIT#p218_revokingARoleThatIsNotHeldChangesNothing` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java:69`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRevokeRoleIT#p218_unknownRoleCodeIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java:89`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRevokeRoleIT#p219_sensitiveRolesCanBeRevokedByTheCommand` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java:157`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRevokeRoleIT#p219_witnessIsRequiredWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java:124`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorRevokeRoleIT#p219_witnessMayBeLeftOutWithTheModeOn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorRevokeRoleIT.java:139`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorSeparationReasonIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorSeparationReasonIT#p215_enoughRemainingGivesTheSameReason` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorSeparationReasonIT.java:81`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorSeparationReasonIT#p215_oneRemainingIsAShortageOnlyWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorSeparationReasonIT.java:64`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorSeparationReasonIT#p215_onlyTheMinimumInTheReasonDiffers` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorSeparationReasonIT.java:46`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorSeparationReasonIT#p216_ordinarySeparationsAreNotHeldInEitherMode` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorSeparationReasonIT.java:121`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorSeparationReasonIT#p216_theSoleSuperAdministratorSeparatingIsHeldInBothModes` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorSeparationReasonIT.java:96`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorTwoPersonRegressionIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorTwoPersonRegressionIT#p223_noSelfGrantEither` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorTwoPersonRegressionIT.java:102`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorTwoPersonRegressionIT#p223_sensitiveGrantStillNeedsASecondPerson` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorTwoPersonRegressionIT.java:80`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorTwoPersonRegressionIT#p223_theDatabaseConstraintStillHolds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorTwoPersonRegressionIT.java:122`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java

- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p201_grantRoleWithoutWitnessSucceeds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:39`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p202_markIsWrittenInTheSameTransaction` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:54`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p203_disableAndEnableWithoutWitness` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:101`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p203_grantAlreadyHeldIsNotMarked` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:128`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p203_registerWithoutWitness` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:66`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p203_resetWithoutWitness` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:86`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p203_revokeWithoutWitness` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:143`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p204_operatorIsStillRequired` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:158`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p205_sameEmployeeIsStillRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:194`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p206_aWitnessThatIsGivenButInvalidIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:210`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p207_aMisspeltOptionIsWrongUsageNotAnOmission` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:320`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p207_repeatedEmptyOrValuelessWitnessIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:225`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p208_aGivenWitnessBehavesAsWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:254`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p209_everyOperationNeedsTheWitnessWithTheModeOff` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:288`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p209_withTheModeOffTheWitnessIsRequired` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:271`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#p221_usageNamesRevokeRole` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:307`
- `com.forcitis.one.acceptance.platform.identity.SingleOperatorWitnessOmissionIT#rulingCoord20261017_01_unknownOperatorWithoutWitnessIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/SingleOperatorWitnessOmissionIT.java:178`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/UserIdColumnsIT.java

- `com.forcitis.one.acceptance.platform.identity.UserIdColumnsIT#everyUserIdColumnIsUuid` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/UserIdColumnsIT.java:69`
- `com.forcitis.one.acceptance.platform.identity.UserIdColumnsIT#noTextColumnIsCalledAnId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/UserIdColumnsIT.java:121`
- `com.forcitis.one.acceptance.platform.identity.UserIdColumnsIT#theAccountNamesItsEmployeeInAUniqueColumn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/UserIdColumnsIT.java:85`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/UuidEmployeeIdsPremiseIT.java

- `com.forcitis.one.acceptance.platform.identity.UuidEmployeeIdsPremiseIT#theGatesRunWithUuidEmployeeIds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/UuidEmployeeIdsPremiseIT.java:33`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java

- `com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistorySchemaIT#authorizationRequestsCarryTheBaseColumns` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java:113`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistorySchemaIT#grantColumnsAreTheCardsWithoutDefaults` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java:88`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistorySchemaIT#grantConstraintsAreTheCards` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java:144`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistorySchemaIT#indexesAreTheCards` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java:165`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistorySchemaIT#roleVersionColumnsAreTheCardsWithoutDefaults` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java:60`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistorySchemaIT#roleVersionConstraintsAreTheCards` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java:125`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistorySchemaIT#triggersAreTheCards` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/AuthorizationHistorySchemaIT.java:185`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java

- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#aClosedRowNeverChanges` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:65`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#aRoleWithoutCurrentVersionCannotBeGranted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:218`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#anOpenGrantCanOnlyBeRevokedOnce` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:139`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#anOpenGrantIsClosedBySeparationOrByHand` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:174`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#anOpenVersionCanOnlyBeClosedOnce` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:94`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#anOpenVersionIsClosedByDeletingTheRole` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:123`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#deleteAndTruncateAreRefusedOnBothTables` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:42`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.GrantHistoryAppendOnlyIT#revokingWithoutCauseAndAuditEntryIsRefusedByThePairedCheck` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/GrantHistoryAppendOnlyIT.java:194`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java

- `com.forcitis.one.acceptance.platform.identity.authorization_history.MomentVersionTablesIT#bothTablesHaveExclusionPeriodAndFiniteChecks` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java:78`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.MomentVersionTablesIT#bothTablesHaveTimestamptzPeriodColumns` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java:68`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.MomentVersionTablesIT#grantPeriodsAreFiniteNotInvertedAndDoNotOverlap` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java:142`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.MomentVersionTablesIT#registryAndRevokeListAgree` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java:174`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.MomentVersionTablesIT#roleNamesAreUniqueAtEachMoment` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java:127`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.MomentVersionTablesIT#roleVersionPeriodsAreFiniteAndNotInverted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java:94`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.MomentVersionTablesIT#roleVersionsOfOneRoleDoNotOverlap` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/MomentVersionTablesIT.java:111`

### backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/PermissionCodeSetIT.java

- `com.forcitis.one.acceptance.platform.identity.authorization_history.PermissionCodeSetIT#aDuplicateIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/PermissionCodeSetIT.java:56`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.PermissionCodeSetIT#aNullElementIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/PermissionCodeSetIT.java:48`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.PermissionCodeSetIT#aTwoDimensionalOrMissingArrayIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/PermissionCodeSetIT.java:70`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.PermissionCodeSetIT#anArrayNotInAscendingCOrderIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/PermissionCodeSetIT.java:63`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.PermissionCodeSetIT#normalisedArraysGoThrough` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/PermissionCodeSetIT.java:77`
- `com.forcitis.one.acceptance.platform.identity.authorization_history.PermissionCodeSetIT#theNormalisationFunctionIsImmutableAndStrict` — `backend/src/test/java/com/forcitis/one/acceptance/platform/identity/authorization_history/PermissionCodeSetIT.java:86`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java

- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#accountBelowTheRootFailsTheRun` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:264`
- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#extraBaseTableColumnsAreAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:353`
- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#extraViewColumnStillRefuses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:365`
- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#illegalUnitTypeFailsTheWholeRun` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:222`
- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#migrationRowsDoNotGateStartup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:384`
- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#missingUnitTypeRefusesStartup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:341`
- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#newStatementsEqualThePureViewStatements` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:422`
- `com.forcitis.one.acceptance.platform.integration.A8SourceFlagsIT#unitTypeIsReadByItsDboName` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/A8SourceFlagsIT.java:301`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/AccountingCodeConfirmerAccountsIT.java

- `com.forcitis.one.acceptance.platform.integration.AccountingCodeConfirmerAccountsIT#theRecorderCannotConfirmTheirOwnAccountingCode` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/AccountingCodeConfirmerAccountsIT.java:48`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/EventPayloadScalarIdsIT.java

- `com.forcitis.one.acceptance.platform.integration.EventPayloadScalarIdsIT#accountEventsCarryScalarUserAndRoleIds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/EventPayloadScalarIdsIT.java:194`
- `com.forcitis.one.acceptance.platform.integration.EventPayloadScalarIdsIT#legalEntityRegisteredCarriesScalarIds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/EventPayloadScalarIdsIT.java:163`
- `com.forcitis.one.acceptance.platform.integration.EventPayloadScalarIdsIT#roleCreatedCarriesAScalarId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/EventPayloadScalarIdsIT.java:178`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java

- `com.forcitis.one.acceptance.platform.integration.ExternalSubjectLookupCommandIT#aBlankExternalSubjectIsWrongUsage` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java:218`
- `com.forcitis.one.acceptance.platform.integration.ExternalSubjectLookupCommandIT#aMissingProviderKindIsWrongUsage` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java:206`
- `com.forcitis.one.acceptance.platform.integration.ExternalSubjectLookupCommandIT#anUnknownProviderKindIsWrongUsage` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java:194`
- `com.forcitis.one.acceptance.platform.integration.ExternalSubjectLookupCommandIT#noOutcomeWritesAnAuditEntry` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java:238`
- `com.forcitis.one.acceptance.platform.integration.ExternalSubjectLookupCommandIT#nobodyFoundEndsWithExitCodeOne` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java:226`
- `com.forcitis.one.acceptance.platform.integration.ExternalSubjectLookupCommandIT#theReadOnlyCommandFindsThePersonByWecomUserId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java:158`
- `com.forcitis.one.acceptance.platform.integration.ExternalSubjectLookupCommandIT#theReadOnlyCommandListsEveryoneWithTheLoginName` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ExternalSubjectLookupCommandIT.java:135`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/IntegrationOperationsProfileIT.java

- `com.forcitis.one.acceptance.platform.integration.IntegrationOperationsProfileIT#theCommandRunsInTheProfile` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/IntegrationOperationsProfileIT.java:154`
- `com.forcitis.one.acceptance.platform.integration.IntegrationOperationsProfileIT#theProfileRunsNoMigration` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/IntegrationOperationsProfileIT.java:138`
- `com.forcitis.one.acceptance.platform.integration.IntegrationOperationsProfileIT#theProfileSchedulesNothing` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/IntegrationOperationsProfileIT.java:145`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyAdapterPlacementTest.java

- `com.forcitis.one.acceptance.platform.integration.LegacyAdapterPlacementTest#anOutsideUserOfTheConnectionPortIsReported` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyAdapterPlacementTest.java:89`
- `com.forcitis.one.acceptance.platform.integration.LegacyAdapterPlacementTest#approvalUsesOnlyTheIntegrationApi` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyAdapterPlacementTest.java:97`
- `com.forcitis.one.acceptance.platform.integration.LegacyAdapterPlacementTest#onlyIntegrationObtainsExternalConnections` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyAdapterPlacementTest.java:72`
- `com.forcitis.one.acceptance.platform.integration.LegacyAdapterPlacementTest#theOldToDoReaderLivesInIntegration` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyAdapterPlacementTest.java:39`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyReferencesIT.java

- `com.forcitis.one.acceptance.platform.integration.LegacyReferencesIT#aShortNameWithoutReferenceIsNeitherWrittenNorCleared` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyReferencesIT.java:107`
- `com.forcitis.one.acceptance.platform.integration.LegacyReferencesIT#withALegacyReferenceTheOldToDosComeThrough` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyReferencesIT.java:89`
- `com.forcitis.one.acceptance.platform.integration.LegacyReferencesIT#withoutALegacyReferenceTheOldToDosAreUnavailable` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/LegacyReferencesIT.java:71`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/NegativeSourceIdIT.java

- `com.forcitis.one.acceptance.platform.integration.NegativeSourceIdIT#negativeSourceIdsGoTheWholeWay` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/NegativeSourceIdIT.java:34`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/OutboundDeliveryKeysIT.java

- `com.forcitis.one.acceptance.platform.integration.OutboundDeliveryKeysIT#aCardIsReplacedByOneNoteThatPointsAtIt` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/OutboundDeliveryKeysIT.java:286`
- `com.forcitis.one.acceptance.platform.integration.OutboundDeliveryKeysIT#concurrentDuplicatesLeaveOneRow` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/OutboundDeliveryKeysIT.java:259`
- `com.forcitis.one.acceptance.platform.integration.OutboundDeliveryKeysIT#theSameTaskAndPurposeIsOneRowWhoeverTheRecipientsAre` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/OutboundDeliveryKeysIT.java:222`
- `com.forcitis.one.acceptance.platform.integration.OutboundDeliveryKeysIT#theWeComTaskIdOfARowDoesNotChangeOnRetry` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/OutboundDeliveryKeysIT.java:315`
- `com.forcitis.one.acceptance.platform.integration.OutboundDeliveryKeysIT#twoTransfersByTwoEventsAreTwoRows` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/OutboundDeliveryKeysIT.java:239`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java

- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#aGrantToGuestDoesNotRefuseAnAccountWithItsOwnMsdbUser` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:382`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#aRoleNestedIntoGuestRefusesStartupWhenMsdbIsReachedThroughGuestOnly` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:315`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#agentJobAfterStartupFailsTheRecheckWithoutTheJobName` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:551`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#allThreeBaselineAccountsStartTogether` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:417`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#alterAnySchemaGrantedToGuestRefusesStartupWhenMsdbIsReachedThroughGuestOnly` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:291`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#baselineAccountStarts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:208`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#deviationRefusesStartup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:435`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#fixtureReproducesTheRdsBaseline` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:199`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#guestOnlyAccountWithTheDefaultGuestGrantsStarts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:362`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#guestOwningItsOwnSchemaDoesNotRefuseStartup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:339`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#legacyInboxCheckDoesNotSwallowADatabaseFailure` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:645`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#msdbCounterexampleReallyHoldsItsPrivilegeAndUndoRestoresTheBaseline` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:223`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#msdbDefaultGrantsOfPublicAndTheAgentRolesDoNotRefuseStartup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:269`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#oneOpsShapedMsdbGrantsDoNotRefuseStartup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:251`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#overriddenPublicGrantsDoNotRefuseStartup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:461`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#ownedAgentJobRefusesStartupWithoutTheJobName` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:528`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#postgresqlAddressIsRefusedThroughTheRealFactory` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:583`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#recheckAlertNamesNoSchemaOrObject` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:508`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#startupRefusalNamesNoSchemaOrObject` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:483`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#writeGrantedAfterStartupStopsTheA8AdapterBeforeTheNextSync` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:600`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyAccountCheckIT#writeGrantedAfterStartupStopsTheShortNameAdapterBeforeTheNextRefresh` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyAccountCheckIT.java:622`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java

- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#aGrantToGuestDoesNotMakeAnAccountWithItsOwnMsdbUserViolate` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:677`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#aRoleNestedIntoGuestIsAViolationWhenMsdbIsReachedThroughGuestOnly` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:592`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#aSchemaDenyOverridesOnlyInItsOwnSchema` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:787`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#alterAnySchemaGrantedToGuestIsAViolationWhenMsdbIsReachedThroughGuestOnly` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:562`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#baselineAccountPassesEveryItem` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:265`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#columnGrantsFoldIntoTheTableAndAreOverriddenByTheSchemaDeny` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:821`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#controlAndTakeOwnershipStayViolationsBesideAnOverriddenGrant` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:734`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#deviationIsMarkedAsViolated` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:286`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#exitCodesAreDistinct` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:1051`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#grantOnATypeStaysAViolation` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:843`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#grantsToTheLoginItselfShowTheSelfPlaceholder` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:882`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#guestOnlyAccountCannotReadItsAgentJobsAndIsUndetermined` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:645`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#guestOnlyAccountWithTheDefaultGuestGrantsPasses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:623`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#locatorValuesAreEscaped` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:907`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#missingDatabaseIsUndeterminedWithoutTheMessage` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:966`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#missingOrUnknownConnectionIsWrongUsage` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:1016`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#msdbDefaultGrantsOfPublicAndTheAgentRolesStillPass` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:537`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#msdbOwnUserInASubsetOfTheAgentRolesPasses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:346`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#msdbOwnUserPrivilegeBeyondTheBaselineIsAViolation` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:474`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#msdbOwnUserWithAnExtraRoleIsAViolation` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:366`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#noDenyAnswerLeavesTheGrantUndetermined` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:863`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#oneOpsShapedMsdbGrantsStillPass` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:516`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#ownedAgentJobIsAViolation` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:427`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#passwordOnTheCommandLineIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:999`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#probeSeesAProcessThatListens` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:252`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#publicExecuteOverriddenByTheDatabaseDenyPasses` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:710`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#rdscoreOwnUserInARoleIsAViolation` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:389`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#rdscoreOwnUserWithMoreThanConnectIsAViolation` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:410`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#refusedCatalogQueryIsUndetermined` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:948`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#retiredConnectionNameIsWrongUsageAndNamesTheNewOne` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:1034`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#schemaLevelGrantsCarryTheObjectPlaceholder` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:766`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#systemDatabaseWithAnOwnUserIsLocatedByName` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:320`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#unreachableServerIsUndetermined` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:983`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#unreadableAgentJobsAreUndetermined` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:454`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#violationDoesNotTellTheNamesBehindIt` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:304`
- `com.forcitis.one.acceptance.platform.integration.ReadOnlyVerificationCommandIT#violationTakesPrecedenceOverUndetermined` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java:929`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SignedTicketSubjectIT.java

- `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#aSubjectBeyondSixtyFourBitsIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SignedTicketSubjectIT.java:192`
- `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#aSubjectWithoutReferenceIsRefusedAndAudited` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SignedTicketSubjectIT.java:145`
- `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#theLowestSixtyFourBitSubjectSignsIn` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SignedTicketSubjectIT.java:175`
- `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#theSubjectIsNotTheEmployeeId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SignedTicketSubjectIT.java:158`
- `com.forcitis.one.acceptance.platform.integration.SignedTicketSubjectIT#theSubjectIsResolvedThroughTheSourceReference` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SignedTicketSubjectIT.java:127`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceIdMatchingIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceIdMatchingIT#theSyncMatchesBySourceIdAndCreatesOnlyForNewOnes` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceIdMatchingIT.java:37`
- `com.forcitis.one.acceptance.platform.integration.SourceIdMatchingIT#twoPeopleWithTheSameCodeBothGetAnEntry` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceIdMatchingIT.java:69`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceAllocationIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceReferenceAllocationIT#concurrentFirstSightGivesOneOwnId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceAllocationIT.java:100`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceAllocationIT#negativeSourceIdsAreOrdinary` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceAllocationIT.java:128`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceAllocationIT#outboundWithoutAReferenceFailsAndAlerts` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceAllocationIT.java:170`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceAllocationIT#theObjectKindIsPartOfTheKey` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceAllocationIT.java:146`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceAllocationIT#theReferenceSurvivesARolledBackDomainWrite` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceAllocationIT.java:156`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#aCorrectionBeforeTheCodeStartsIsStillRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:215`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#aCorrectionFromTheSameDayReplacesAWrongCode` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:163`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#aSameDayCorrectionStillNeedsASecondPerson` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:194`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#concurrentCorrectionsConfirmOnlyOne` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:272`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#confirmingATakenCodeFails` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:114`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#correctionClosesTheOldRowOnConfirmation` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:75`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#everyStepIsAudited` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:130`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#fourEyes` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:41`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#neverConfirmedMeansNoOutbound` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:62`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConfirmationIT#rejectionLeavesEverythingElseAlone` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConfirmationIT.java:93`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#accountingCodeCannotPassAsASynchronisedRow` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:288`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#anEmptyPeriodTakesNoKey` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:387`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#columnsAndTypes` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:243`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#confirmedRowsAreUniqueAtEveryPointInTime` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:301`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#manualRowIsNeverDecidedWithoutADecider` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:336`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#pendingAndRejectedRowsTakeNoKey` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:323`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#referencesAreNeverDeleted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:352`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#sourceIdAndOwnIdAreNeverRewritten` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:407`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceConstraintIT#valueChecks` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceConstraintIT.java:267`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceCursorOrderIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceReferenceCursorOrderIT#pagingFollowsTheMomentsNotTheIds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceCursorOrderIT.java:55`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceManualEditIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#invalidInputIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceManualEditIT.java:69`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#onlyTheAccountingCodeOfALegalEntityIsEditable` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceManualEditIT.java:47`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#registeringNeedsThePermission` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceManualEditIT.java:31`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceManualEditIT#thePermissionsAndWhoHoldsThem` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceManualEditIT.java:88`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceQueryIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#listingNeedsUpdateOrApprove` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceQueryIT.java:138`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#oneLegalEntityShowsItsCurrentCodeAndHistory` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceQueryIT.java:155`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#onlyTheManualCategoryIsListed` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceQueryIT.java:188`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceQueryIT#theApproverFindsWhatWaits` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceQueryIT.java:90`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceRefusedRenameIT.java

- `com.forcitis.one.acceptance.platform.integration.SourceReferenceRefusedRenameIT#aRefusedRegistrationIsPublishedAsSourceReferenceRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceRefusedRenameIT.java:51`
- `com.forcitis.one.acceptance.platform.integration.SourceReferenceRefusedRenameIT#theCataloguesUseOnlyTheNewName` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/SourceReferenceRefusedRenameIT.java:42`

### backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java

- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#controlWithoutAnyStall` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:228`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#everyExternalSessionCarriesALockTimeout` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:322`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#reviewH2_slowA8ViewIsCancelledOnTheServerAfterTheLoginLookupGivesUp` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:348`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#reviewM1_concurrentStalledInboxRequestsAllAnswerWithinTheBudget` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:373`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#slowA8ViewWithoutLocksDegradesTheLoginLookup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:299`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#slowLegacyInboxWithoutLocksDegradesToo` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:257`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#stalledA8ViewsDegradeTheLoginLookup` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:289`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#stalledA8ViewsFailTheSyncWithAnAlert` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:273`
- `com.forcitis.one.acceptance.platform.integration.StalledExternalSourceIT#stalledLegacyInboxDegradesTheMergedInbox` — `backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java:239`

### backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/IssuedNumberNonNullTest.java

- `com.forcitis.one.acceptance.platform.numbering.IssuedNumberNonNullTest#issuedNumberRefusesAMissingRuleVersionId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/IssuedNumberNonNullTest.java:21`
- `com.forcitis.one.acceptance.platform.numbering.IssuedNumberNonNullTest#ruleVersionIdRefusesANullValue` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/IssuedNumberNonNullTest.java:28`

### backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/LegacyNumberSourceIT.java

- `com.forcitis.one.acceptance.platform.numbering.LegacyNumberSourceIT#appendOnly` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/LegacyNumberSourceIT.java:183`
- `com.forcitis.one.acceptance.platform.numbering.LegacyNumberSourceIT#columns` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/LegacyNumberSourceIT.java:129`
- `com.forcitis.one.acceptance.platform.numbering.LegacyNumberSourceIT#theOldBatchTermIsGone` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/LegacyNumberSourceIT.java:217`
- `com.forcitis.one.acceptance.platform.numbering.LegacyNumberSourceIT#uniquePerSourceSystem` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/LegacyNumberSourceIT.java:160`

### backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java

- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#businessConfirmationOnUnconfirmedStatusStillAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:77`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#confirmedWithConfirmerAndPendingWithoutAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:69`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#confirmedWithoutConfirmerRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:61`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#illegalPeriodKeysRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:128`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#lastValueInRangeAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:136`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#lastValueOutOfRangeRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:143`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#legalPeriodKeysAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:120`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#numberingKeepsQuoteAndJournalVoucher` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:110`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#retiredColumnsAreGone` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:85`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#seedsKeepTheirTemplateAndStartAtEventCountZero` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:96`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRowRulesIT#unknownCounterDocumentTypeRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:151`

### backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleConfirmerIT.java

- `com.forcitis.one.acceptance.platform.numbering.NumberingRuleConfirmerIT#theAuthorOfAVersionCannotConfirmIt` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleConfirmerIT.java:46`

### backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleVersionIdIT.java

- `com.forcitis.one.acceptance.platform.numbering.NumberingRuleVersionIdIT#aSupersededVersionKeepsItsId` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleVersionIdIT.java:196`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRuleVersionIdIT#aVersionKeepsItsIdWhenItsStatusMoves` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleVersionIdIT.java:180`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRuleVersionIdIT#eachBusinessDateGetsTheIdOfTheVersionInForceThen` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleVersionIdIT.java:166`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRuleVersionIdIT#insertedRowCarriesTheIdCreatedWhenTheVersionWasProposed` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleVersionIdIT.java:210`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRuleVersionIdIT#issuedNumberNamesTheVersionRowItWasIssuedUnder` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleVersionIdIT.java:158`
- `com.forcitis.one.acceptance.platform.numbering.NumberingRuleVersionIdIT#sameVersionGivesTheSameIdEveryTime` — `backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRuleVersionIdIT.java:221`

### backend/src/test/java/com/forcitis/one/acceptance/platform/web/ErrorLogDetailIT.java

- `com.forcitis.one.acceptance.platform.web.ErrorLogDetailIT#unexpectedFailureIsLoggedWithClassNameAndCorrelationIdOnly` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ErrorLogDetailIT.java:161`
- `com.forcitis.one.acceptance.platform.web.ErrorLogDetailIT#uniqueViolationDoesNotPutTheConflictingValueInTheLog` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ErrorLogDetailIT.java:141`

### backend/src/test/java/com/forcitis/one/acceptance/platform/web/IdQueryParameterIT.java

- `com.forcitis.one.acceptance.platform.web.IdQueryParameterIT#aMalformedIdIsABadRequest` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/IdQueryParameterIT.java:84`
- `com.forcitis.one.acceptance.platform.web.IdQueryParameterIT#aWellFormedIdIsAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/IdQueryParameterIT.java:105`
- `com.forcitis.one.acceptance.platform.web.IdQueryParameterIT#theOldSubjectKeyParameterIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/IdQueryParameterIT.java:119`

### backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java

- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#actuatorAndNonApiPathsIgnoreTheUnknownParameterRule` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:228`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#apiDocsIgnoresTheUnknownParameterRule` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:220`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#cursorOfAnotherListIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:241`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#cursorThatIsNotOneThisServerIssuedIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:254`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#cursorWhoseKeyHasTheWrongTypeIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:275`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#cursorWithAlteredFirstByteIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:265`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#defaultPageIsFiftyRows` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:149`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#everyLimitParameterInTheOpenApiDocumentDeclaresTheBounds` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:300`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#issuedCursorCanBeReplayedAsIs` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:286`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#limitBoundariesOneAndTwoHundredAreAccepted` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:142`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#limitOutsideOneToTwoHundredIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:133`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#unregisteredParameterNextToRegisteredOnesIsStillRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:198`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#unregisteredParameterOnASingleResourceReadIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:207`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#unregisteredQueryParameterIsRefusedByName` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:186`
- `com.forcitis.one.acceptance.platform.web.ListPagingContractIT#walkingTheLegalEntitiesCoversEveryRowOnce` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ListPagingContractIT.java:163`

### backend/src/test/java/com/forcitis/one/acceptance/platform/web/ManagementEndpointIT.java

- `com.forcitis.one.acceptance.platform.web.ManagementEndpointIT#anonymousScrapeOnApplicationPortIsRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ManagementEndpointIT.java:89`
- `com.forcitis.one.acceptance.platform.web.ManagementEndpointIT#anonymousScrapeOnManagementPortReturnsMetrics` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ManagementEndpointIT.java:78`
- `com.forcitis.one.acceptance.platform.web.ManagementEndpointIT#otherActuatorEndpointsOnManagementPortAreRefused` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/ManagementEndpointIT.java:98`

### backend/src/test/java/com/forcitis/one/acceptance/platform/web/OpenApiUuidParametersIT.java

- `com.forcitis.one.acceptance.platform.web.OpenApiUuidParametersIT#everyUuidParameterRefusesWhatIsNotAUuid` — `backend/src/test/java/com/forcitis/one/acceptance/platform/web/OpenApiUuidParametersIT.java:116`

### backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java

- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#aStaleCopyIsNotSaved` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:488`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#concurrentFirstSubmissionsOfOneTimesheetCreateOne` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:504`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#foreignAndMissingTimesheetsAreIndistinguishable` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:401`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#missingLegalEntityIsRefused` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:475`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#redeliveredSubmissionStartsNothingNew` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:233`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#resubmittingBeforeTheResultArrivedIsRefused` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:349`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#resultsForUnknownTimesheetsAreCounted` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:286`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#strangersCannotTouchSomeoneElsesTimesheet` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:384`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#unknownProjectManagerIsRefused` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:463`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#versionAndEventSequenceAreStored` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:451`
- `com.forcitis.one.cost.timesheet.application.TimesheetApprovalScenarioIT#workDateAndTheNumberAndDepartmentAreStored` — `backend/src/test/java/com/forcitis/one/cost/timesheet/application/TimesheetApprovalScenarioIT.java:433`

### backend/src/test/java/com/forcitis/one/cost/timesheet/domain/TimesheetStateMachineTest.java

- `com.forcitis.one.cost.timesheet.domain.TimesheetStateMachineTest#eventNumbersHaveNoGaps` — `backend/src/test/java/com/forcitis/one/cost/timesheet/domain/TimesheetStateMachineTest.java:212`
- `com.forcitis.one.cost.timesheet.domain.TimesheetStateMachineTest#onlyTheOwnerChangesAndSubmits` — `backend/src/test/java/com/forcitis/one/cost/timesheet/domain/TimesheetStateMachineTest.java:81`
- `com.forcitis.one.cost.timesheet.domain.TimesheetStateMachineTest#theWorkDateIsEnteredAndKept` — `backend/src/test/java/com/forcitis/one/cost/timesheet/domain/TimesheetStateMachineTest.java:90`

### backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java

- `com.forcitis.one.organization.workforce.application.OrgDirectoryIntegrationTest#concurrentFirstLoginsRegisterOnce` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java:80`
- `com.forcitis.one.organization.workforce.application.OrgDirectoryIntegrationTest#loginMissFailsClosed` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java:128`
- `com.forcitis.one.organization.workforce.application.OrgDirectoryIntegrationTest#loginMissRegistersALeaverAsSeparated` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java:209`
- `com.forcitis.one.organization.workforce.application.OrgDirectoryIntegrationTest#loginMissRegistersTheNewHire` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java:108`
- `com.forcitis.one.organization.workforce.application.OrgDirectoryIntegrationTest#loginNameIsAnsweredBySnapshot` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java:152`
- `com.forcitis.one.organization.workforce.application.OrgDirectoryIntegrationTest#loginNameMissFailsClosed` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java:185`
- `com.forcitis.one.organization.workforce.application.OrgDirectoryIntegrationTest#loginNameMissRegistersTheNewHire` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgDirectoryIntegrationTest.java:167`

### backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java

- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#applyingTheSnapshotIsARegisteredMultiAggregateWrite` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:83`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#concurrentRunsDoNotDuplicate` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:359`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#interruptedRunsAreClosed` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:190`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#recoveredSourceClosesTheHold` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:314`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#releaseReasonLosesControlCharacters` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:273`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#shortNamesAreRefreshedAndKept` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:400`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#snapshotDayIsTheSameForTheEventAndTheHistoryRowsAcrossMidnight` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:133`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#unavailableSourceFailsTheRun` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:234`
- `com.forcitis.one.organization.workforce.application.OrgSnapshotSyncIntegrationTest#wecomUserIdCanMoveBetweenPeopleInOneRun` — `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java:384`

### backend/src/test/java/com/forcitis/one/organization/workforce/domain/EmployeeSyncRulesTest.java

- `com.forcitis.one.organization.workforce.domain.EmployeeSyncRulesTest#nullInternalFlagIsSyncedAndReported` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/EmployeeSyncRulesTest.java:45`

### backend/src/test/java/com/forcitis/one/organization/workforce/domain/LegalEntityAttributionTest.java

- `com.forcitis.one.organization.workforce.domain.LegalEntityAttributionTest#attributingClosesTheOpenPeriod` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/LegalEntityAttributionTest.java:42`
- `com.forcitis.one.organization.workforce.domain.LegalEntityAttributionTest#events` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/LegalEntityAttributionTest.java:55`
- `com.forcitis.one.organization.workforce.domain.LegalEntityAttributionTest#historyIsNotRewritten` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/LegalEntityAttributionTest.java:91`
- `com.forcitis.one.organization.workforce.domain.LegalEntityAttributionTest#resolution` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/LegalEntityAttributionTest.java:133`
- `com.forcitis.one.organization.workforce.domain.LegalEntityAttributionTest#sameEntityIsANoOp` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/LegalEntityAttributionTest.java:78`

### backend/src/test/java/com/forcitis/one/organization/workforce/domain/OrgUnitSyncRulesTest.java

- `com.forcitis.one.organization.workforce.domain.OrgUnitSyncRulesTest#aStoredParentIsKnown` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/OrgUnitSyncRulesTest.java:157`
- `com.forcitis.one.organization.workforce.domain.OrgUnitSyncRulesTest#unknownParentIsReported` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/OrgUnitSyncRulesTest.java:144`
- `com.forcitis.one.organization.workforce.domain.OrgUnitSyncRulesTest#unknownRegionalManagerIsReported` — `backend/src/test/java/com/forcitis/one/organization/workforce/domain/OrgUnitSyncRulesTest.java:130`

### backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java

- `com.forcitis.one.organization.workforce.guard.WorkforceSchemaGuardTest#apiExposesNoMoney` — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:91`
- `com.forcitis.one.organization.workforce.guard.WorkforceSchemaGuardTest#moduleUsesNoMoney` — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:102`
- `com.forcitis.one.organization.workforce.guard.WorkforceSchemaGuardTest#noMoneyColumns` — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:33`
- `com.forcitis.one.organization.workforce.guard.WorkforceSchemaGuardTest#patternCatchesMoney` — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:79`

### backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java

- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#aClaimedTaskEscalatesToTheSuperior` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:275`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#aLeftoverTaskOfAnEarlierRoundIsClosed` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:354`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#anUnclaimedTaskEscalatesToTheAdministrator` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:300`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#createdOnADayOffCountsFromTheNextWorkday` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:188`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#decidedInTimeIsNotReminded` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:262`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#holidayMovesTheDeadline` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:166`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#lateRunCatchesUp` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:339`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#makeUpWorkdayCounts` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:177`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#noSuperiorGoesToTheAdministrator` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:323`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#numbersComeFromTheNode` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:198`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#reminderOnceAndNeverAnApproval` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:230`
- `com.forcitis.one.platform.approval.ApprovalDeadlineIT#weekendDoesNotCount` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalDeadlineIT.java:156`

### backend/src/test/java/com/forcitis/one/platform/approval/ApprovalStartOwnershipIT.java

- `com.forcitis.one.platform.approval.ApprovalStartOwnershipIT#anotherUserCannotStartSomeoneElsesApproval` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalStartOwnershipIT.java:49`
- `com.forcitis.one.platform.approval.ApprovalStartOwnershipIT#theRefusalNamesTheCallerNotTheInitiatorInTheRequest` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalStartOwnershipIT.java:76`
- `com.forcitis.one.platform.approval.ApprovalStartOwnershipIT#thereIsNoHttpStart` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalStartOwnershipIT.java:132`

### backend/src/test/java/com/forcitis/one/platform/approval/ApprovalTaskHttpIT.java

- `com.forcitis.one.platform.approval.ApprovalTaskHttpIT#anonymousAndCsrf` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalTaskHttpIT.java:80`
- `com.forcitis.one.platform.approval.ApprovalTaskHttpIT#claimAndDecideOverHttp` — `backend/src/test/java/com/forcitis/one/platform/approval/ApprovalTaskHttpIT.java:29`

### backend/src/test/java/com/forcitis/one/platform/approval/IdempotentStartIT.java

- `com.forcitis.one.platform.approval.IdempotentStartIT#rolledBackStartLeavesNothing` — `backend/src/test/java/com/forcitis/one/platform/approval/IdempotentStartIT.java:211`
- `com.forcitis.one.platform.approval.IdempotentStartIT#roundsAreAskedForInOrder` — `backend/src/test/java/com/forcitis/one/platform/approval/IdempotentStartIT.java:169`
- `com.forcitis.one.platform.approval.IdempotentStartIT#whichEndedApprovalsMayStartAgain` — `backend/src/test/java/com/forcitis/one/platform/approval/IdempotentStartIT.java:189`

### backend/src/test/java/com/forcitis/one/platform/approval/MergedInboxIT.java

- `com.forcitis.one.platform.approval.MergedInboxIT#groupTasksFollowTheClaim` — `backend/src/test/java/com/forcitis/one/platform/approval/MergedInboxIT.java:157`
- `com.forcitis.one.platform.approval.MergedInboxIT#oldAndNewCountsAreConsistent` — `backend/src/test/java/com/forcitis/one/platform/approval/MergedInboxIT.java:75`
- `com.forcitis.one.platform.approval.MergedInboxIT#theInboxIsPaged` — `backend/src/test/java/com/forcitis/one/platform/approval/MergedInboxIT.java:178`
- `com.forcitis.one.platform.approval.MergedInboxIT#theOldViewIsReadLiveEveryTime` — `backend/src/test/java/com/forcitis/one/platform/approval/MergedInboxIT.java:128`
- `com.forcitis.one.platform.approval.MergedInboxIT#withoutReferenceTheOldToDosAreUnavailable` — `backend/src/test/java/com/forcitis/one/platform/approval/MergedInboxIT.java:142`

### backend/src/test/java/com/forcitis/one/platform/approval/NodeRoutingIT.java

- `com.forcitis.one.platform.approval.NodeRoutingIT#anUnknownFlagIsRefused` — `backend/src/test/java/com/forcitis/one/platform/approval/NodeRoutingIT.java:73`
- `com.forcitis.one.platform.approval.NodeRoutingIT#anUnpairedDeadlineFailsTheStart` — `backend/src/test/java/com/forcitis/one/platform/approval/NodeRoutingIT.java:91`
- `com.forcitis.one.platform.approval.NodeRoutingIT#shippedProcessIsDeployed` — `backend/src/test/java/com/forcitis/one/platform/approval/NodeRoutingIT.java:146`
- `com.forcitis.one.platform.approval.NodeRoutingIT#shippedProcessRoutesByItsAttributes` — `backend/src/test/java/com/forcitis/one/platform/approval/NodeRoutingIT.java:46`
- `com.forcitis.one.platform.approval.NodeRoutingIT#theProcessDeclaresItsFlags` — `backend/src/test/java/com/forcitis/one/platform/approval/NodeRoutingIT.java:66`

### backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java

- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#afterAClaimOnlyTheClaimerActs` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:131`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#bodyCannotNameTheActor` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:179`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#noPermissionCodeIsNeededToAct` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:252`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#nonHandlersAreForbiddenOverHttp` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:100`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#oneForeignTaskRefusesTheWholeBatch` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:163`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#refusalSurvivesAFailingAudit` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:117`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#strangerAndUnknownTaskAreIndistinguishable` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:286`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#unknownTaskIsNotFound` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:277`
- `com.forcitis.one.platform.approval.TaskActorAuthorizationIT#useCasesRefuseNonHandlers` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskActorAuthorizationIT.java:150`

### backend/src/test/java/com/forcitis/one/platform/approval/TaskNotificationIT.java

- `com.forcitis.one.platform.approval.TaskNotificationIT#claimMarksTheCardHandled` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskNotificationIT.java:80`
- `com.forcitis.one.platform.approval.TaskNotificationIT#decidingAnUnclaimedTaskMarksTheCardHandled` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskNotificationIT.java:97`
- `com.forcitis.one.platform.approval.TaskNotificationIT#everyCandidateGetsTheCard` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskNotificationIT.java:57`
- `com.forcitis.one.platform.approval.TaskNotificationIT#nobodyReachableSendsNothing` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskNotificationIT.java:126`
- `com.forcitis.one.platform.approval.TaskNotificationIT#returnSendsTheCompletionCardOnly` — `backend/src/test/java/com/forcitis/one/platform/approval/TaskNotificationIT.java:139`

### backend/src/test/java/com/forcitis/one/platform/approval/TimesheetBranchesIT.java

- `com.forcitis.one.platform.approval.TimesheetBranchesIT#allThreeBranches` — `backend/src/test/java/com/forcitis/one/platform/approval/TimesheetBranchesIT.java:96`
- `com.forcitis.one.platform.approval.TimesheetBranchesIT#noRoutingFlagIsRefused` — `backend/src/test/java/com/forcitis/one/platform/approval/TimesheetBranchesIT.java:213`
- `com.forcitis.one.platform.approval.TimesheetBranchesIT#onlyProjectHours` — `backend/src/test/java/com/forcitis/one/platform/approval/TimesheetBranchesIT.java:69`
- `com.forcitis.one.platform.approval.TimesheetBranchesIT#projectAndPreSalesRunInParallel` — `backend/src/test/java/com/forcitis/one/platform/approval/TimesheetBranchesIT.java:81`
- `com.forcitis.one.platform.approval.TimesheetBranchesIT#rejectionInOneBranchEndsTheRound` — `backend/src/test/java/com/forcitis/one/platform/approval/TimesheetBranchesIT.java:112`

### backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalRequestStateMachineTest.java

- `com.forcitis.one.platform.approval.domain.ApprovalRequestStateMachineTest#anEndingWithoutAReasonIsRefused` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalRequestStateMachineTest.java:221`
- `com.forcitis.one.platform.approval.domain.ApprovalRequestStateMachineTest#approvedIsFinal` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalRequestStateMachineTest.java:92`
- `com.forcitis.one.platform.approval.domain.ApprovalRequestStateMachineTest#eventNumbersHaveNoGaps` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalRequestStateMachineTest.java:242`
- `com.forcitis.one.platform.approval.domain.ApprovalRequestStateMachineTest#runningCannotRestart` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalRequestStateMachineTest.java:82`
- `com.forcitis.one.platform.approval.domain.ApprovalRequestStateMachineTest#startsAndRestartsRecordTheRound` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalRequestStateMachineTest.java:144`

### backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalTaskStateMachineTest.java

- `com.forcitis.one.platform.approval.domain.ApprovalTaskStateMachineTest#aRejectionWithoutAReasonIsRefused` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalTaskStateMachineTest.java:164`
- `com.forcitis.one.platform.approval.domain.ApprovalTaskStateMachineTest#anOldTaskIsClosedAlone` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalTaskStateMachineTest.java:308`
- `com.forcitis.one.platform.approval.domain.ApprovalTaskStateMachineTest#escalationFollowsTheReminder` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalTaskStateMachineTest.java:114`
- `com.forcitis.one.platform.approval.domain.ApprovalTaskStateMachineTest#noDeadlineNoOverdue` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalTaskStateMachineTest.java:144`
- `com.forcitis.one.platform.approval.domain.ApprovalTaskStateMachineTest#overdueOnceAndNotEarly` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalTaskStateMachineTest.java:133`
- `com.forcitis.one.platform.approval.domain.ApprovalTaskStateMachineTest#releaseAndReassign` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/ApprovalTaskStateMachineTest.java:291`

### backend/src/test/java/com/forcitis/one/platform/approval/domain/AssigneeResolverTest.java

- `com.forcitis.one.platform.approval.domain.AssigneeResolverTest#deferredAndNotBuiltTypesAreRefused` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/AssigneeResolverTest.java:301`
- `com.forcitis.one.platform.approval.domain.AssigneeResolverTest#departmentIsOptionalForTheDepartmentHead` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/AssigneeResolverTest.java:292`
- `com.forcitis.one.platform.approval.domain.AssigneeResolverTest#garbageAmongPeopleIsRefused` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/AssigneeResolverTest.java:209`
- `com.forcitis.one.platform.approval.domain.AssigneeResolverTest#parseEmployeesRefusesTheWholeList` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/AssigneeResolverTest.java:217`
- `com.forcitis.one.platform.approval.domain.AssigneeResolverTest#unitThatIsNoOrgUnitIdIsRefused` — `backend/src/test/java/com/forcitis/one/platform/approval/domain/AssigneeResolverTest.java:102`

### backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java

- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#aFlagMustBeTrueOrFalse` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:200`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#deadlineParametersComeAsAPair` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:181`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#deferredAndNotOfferedTypesDoNotLoad` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:104`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#departmentHeadNodeHasAnOptionalDepartment` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:223`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#engineVariableMapping` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:235`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#invalidRuleNamesTheNode` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:81`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#noProcessFileUsesARemovedType` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:117`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#shippedProcessesAreValid` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:92`
- `com.forcitis.one.platform.approval.infrastructure.flowable.BpmnRuleConventionsTest#timesheetProcessUsesThePublishedNames` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/flowable/BpmnRuleConventionsTest.java:140`

### backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java

- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#branchTableEqualsTheProcess` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:154`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#classifiesEveryNode` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:30`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#cleanExportPasses` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:89`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#commandLineEntry` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:170`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#deterministicAndIdsOnly` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:80`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#exportInOldNamesReplays` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:56`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#nodeSetDifferencesAreDefects` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:124`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#oldNamesAreTranslatedByTheTable` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:234`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#ruleVocabulary` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:203`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#skippedNodeMatches` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:116`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#supervisorKeyAlias` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:263`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#threeShapesMatch` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:104`
- `com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayIT#vocabularyTable` — `backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplayIT.java:142`

### backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java

- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#administratorModeForOneHour` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:259`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#administratorModeIsNotExtended` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:282`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#boundAccountMustBeEnabled` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:248`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#boundPersonMustHoldSuperAdmin` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:235`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#codeCannotBeReplayed` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:102`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#currentSnapshotWithoutTheBoundEmployeeIsRefused` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:222`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#emergencySessionHasPlatformPermissionsOnly` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:325`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#forwardedHeaderFromAnOutsiderIsIgnored` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:186`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#internalNetworkOnly` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:175`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#login` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:78`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#secretsAreNotStoredInTheClear` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:91`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#separatedPersonIsRefused` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:198`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#sessionEndsWhenSuperAdminIsTakenAway` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:297`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#sessionEndsWhenTheEmergencyAccountIsDisabled` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:311`
- `com.forcitis.one.platform.identity.EmergencyLoginIntegrationTest#unavailableSnapshotIsAllowed` — `backend/src/test/java/com/forcitis/one/platform/identity/EmergencyLoginIntegrationTest.java:210`

### backend/src/test/java/com/forcitis/one/platform/identity/OneTimeCredentialConcurrencyIntegrationTest.java

- `com.forcitis.one.platform.identity.OneTimeCredentialConcurrencyIntegrationTest#signedTicketNonce` — `backend/src/test/java/com/forcitis/one/platform/identity/OneTimeCredentialConcurrencyIntegrationTest.java:71`

### backend/src/test/java/com/forcitis/one/platform/identity/RoleManagementIntegrationTest.java

- `com.forcitis.one.platform.identity.RoleManagementIntegrationTest#accountWritesNeedTheVersion` — `backend/src/test/java/com/forcitis/one/platform/identity/RoleManagementIntegrationTest.java:278`
- `com.forcitis.one.platform.identity.RoleManagementIntegrationTest#platformPermissionsStayInTheAdministratorMode` — `backend/src/test/java/com/forcitis/one/platform/identity/RoleManagementIntegrationTest.java:340`
- `com.forcitis.one.platform.identity.RoleManagementIntegrationTest#roleIdsAreCanonicalUuids` — `backend/src/test/java/com/forcitis/one/platform/identity/RoleManagementIntegrationTest.java:95`
- `com.forcitis.one.platform.identity.RoleManagementIntegrationTest#sameRolesChangeNothing` — `backend/src/test/java/com/forcitis/one/platform/identity/RoleManagementIntegrationTest.java:303`
- `com.forcitis.one.platform.identity.RoleManagementIntegrationTest#storedCountersFollowTheChanges` — `backend/src/test/java/com/forcitis/one/platform/identity/RoleManagementIntegrationTest.java:317`

### backend/src/test/java/com/forcitis/one/platform/identity/SessionLifecycleIntegrationTest.java

- `com.forcitis.one.platform.identity.SessionLifecycleIntegrationTest#csrfHandshake` — `backend/src/test/java/com/forcitis/one/platform/identity/SessionLifecycleIntegrationTest.java:237`
- `com.forcitis.one.platform.identity.SessionLifecycleIntegrationTest#expiredSessionsAreAuditedThenRemoved` — `backend/src/test/java/com/forcitis/one/platform/identity/SessionLifecycleIntegrationTest.java:168`

### backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java

- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#administratorsAreRefused` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:154`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#forgeries` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:118`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#issuerIsOffByDefault` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:77`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#keyResolution` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:141`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#nonceIsOneTime` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:89`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#subjectWithoutSourceReference` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:200`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#ticketSessionNeedsConfirmation` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:217`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#timeWindow` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:102`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#userScope` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:168`
- `com.forcitis.one.platform.identity.SignedTicketLoginIntegrationTest#validTicket` — `backend/src/test/java/com/forcitis/one/platform/identity/SignedTicketLoginIntegrationTest.java:63`

### backend/src/test/java/com/forcitis/one/platform/identity/SsoLoginIntegrationTest.java

- `com.forcitis.one.platform.identity.SsoLoginIntegrationTest#refusalAtOddPathsIsStillAudited` — `backend/src/test/java/com/forcitis/one/platform/identity/SsoLoginIntegrationTest.java:218`
- `com.forcitis.one.platform.identity.SsoLoginIntegrationTest#ticketSessionNeedsConfirmation` — `backend/src/test/java/com/forcitis/one/platform/identity/SsoLoginIntegrationTest.java:184`
- `com.forcitis.one.platform.identity.SsoLoginIntegrationTest#unknownLoginNameStaysOutOfDetail` — `backend/src/test/java/com/forcitis/one/platform/identity/SsoLoginIntegrationTest.java:89`

### backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java

- `com.forcitis.one.platform.identity.WecomLoginIntegrationTest#bindingCookie` — `backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java:244`
- `com.forcitis.one.platform.identity.WecomLoginIntegrationTest#expiredState` — `backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java:155`
- `com.forcitis.one.platform.identity.WecomLoginIntegrationTest#id12UnusableAddressDoesNotFailTheLogin` — `backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java:307`
- `com.forcitis.one.platform.identity.WecomLoginIntegrationTest#id12ZonedIpv6IsStored` — `backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java:324`
- `com.forcitis.one.platform.identity.WecomLoginIntegrationTest#stateFromAnotherBrowser` — `backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java:261`
- `com.forcitis.one.platform.identity.WecomLoginIntegrationTest#stateRules` — `backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java:138`
- `com.forcitis.one.platform.identity.WecomLoginIntegrationTest#unboundWecomUserStaysOutOfDetail` — `backend/src/test/java/com/forcitis/one/platform/identity/WecomLoginIntegrationTest.java:115`

### backend/src/test/java/com/forcitis/one/platform/identity/domain/AccessScopeTest.java

- `com.forcitis.one.platform.identity.domain.AccessScopeTest#legalEntityDimension` — `backend/src/test/java/com/forcitis/one/platform/identity/domain/AccessScopeTest.java:129`
- `com.forcitis.one.platform.identity.domain.AccessScopeTest#projectRoleDimension` — `backend/src/test/java/com/forcitis/one/platform/identity/domain/AccessScopeTest.java:138`

### backend/src/test/java/com/forcitis/one/platform/identity/domain/RoleTest.java

- `com.forcitis.one.platform.identity.domain.RoleTest#customRoleCannotCarryAdministratorModePermission` — `backend/src/test/java/com/forcitis/one/platform/identity/domain/RoleTest.java:114`
- `com.forcitis.one.platform.identity.domain.RoleTest#superAdmin` — `backend/src/test/java/com/forcitis/one/platform/identity/domain/RoleTest.java:87`

### backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java

- `com.forcitis.one.platform.integration.WecomDeliveryIT#cardIsSentAfterCommitByTheJob` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:161`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#exhaustedReplacementIsADeadLetter` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:384`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#exhaustedRetriesEndInTheDeadLetters` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:281`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#expiredLeaseIsReclaimedAndTheStaleWriteIsRejected` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:435`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#failedSendIsRetried` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:260`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#failingReplacementIsRetried` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:363`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#handledBeforeDeliveryIsNeverSent` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:416`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#handledWhileTheCardIsInFlightIsStillReplaced` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:341`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#leaseCoversTheWholeBatch` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:475`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#markHandledNeedsTheCallersTransaction` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:429`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#markHandledReplacesTheCard` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:321`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#markHandledWithoutResponseCodeDegrades` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:402`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#overdueMetricCountsStalePendingCards` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:302`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#repeatedSendWithUsedTaskIdIsSent` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:482`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#rollbackQueuesNothing` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:178`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#sameKeyIsSentOnce` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:197`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#sendNeedsTheCallersTransaction` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:191`
- `com.forcitis.one.platform.integration.WecomDeliveryIT#sentRequestNeverBroadcasts` — `backend/src/test/java/com/forcitis/one/platform/integration/WecomDeliveryIT.java:248`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java

- `com.forcitis.one.platform.integration.guard.IntegrationConfigurationTest#credentialsAreEnvironmentPlaceholders` — `backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java:29`
- `com.forcitis.one.platform.integration.guard.IntegrationConfigurationTest#eventRegistryUsesTheIntegrationSchema` — `backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java:67`
- `com.forcitis.one.platform.integration.guard.IntegrationConfigurationTest#safeDefaults` — `backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java:41`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/InboxMergerTest.java

- `com.forcitis.one.platform.integration.infrastructure.legacy.InboxMergerTest#differentNodesAndInstancesStaySeparate` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/InboxMergerTest.java:32`
- `com.forcitis.one.platform.integration.infrastructure.legacy.InboxMergerTest#empty` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/InboxMergerTest.java:57`
- `com.forcitis.one.platform.integration.infrastructure.legacy.InboxMergerTest#rowTasksOfOneNodeMergeIntoTheNewest` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/InboxMergerTest.java:20`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonConfigurationTest.java

- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonConfigurationTest#failedRecheckReadsNothing` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonConfigurationTest.java:95`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonConfigurationTest#offByDefault` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonConfigurationTest.java:48`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonConfigurationTest#refusedLoginStopsTheApplication` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonConfigurationTest.java:116`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonConfigurationTest#snapshotRechecksAndHidesTheFailure` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonConfigurationTest.java:80`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonConfigurationTest#startupCheckStopsTheApplication` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonConfigurationTest.java:105`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonConfigurationTest#viewsBind` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonConfigurationTest.java:61`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonOrgTreeTest.java

- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonOrgTreeTest#accountBelowTheRootIsRejected` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonOrgTreeTest.java:38`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonOrgTreeTest#illegalTypeIsRejected` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonOrgTreeTest.java:47`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonOrgTreeTest#rootPathIsConfigurable` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonOrgTreeTest.java:63`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonOrgTreeTest#rootTypeIsFreeBelowIsDepartment` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonOrgTreeTest.java:31`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonOrgTreeTest#theApiRecordHasNoPath` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonOrgTreeTest.java:116`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java

- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewSqlTest#catalogRead` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java:145`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewSqlTest#noSensitiveColumns` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java:171`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewSqlTest#objectCheckIsNotVacuous` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java:137`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewSqlTest#onlyRegisteredObjectsAndColumns` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java:104`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewSqlTest#onlySelects` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java:25`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewSqlTest#orgUnitsJoinTheBaseTableByPrimaryKey` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java:124`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewSqlTest#statementCheckIsNotVacuous` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewSqlTest.java:112`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java

- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsMssqlIT#baseTablesAreReadable` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java:190`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsMssqlIT#extraColumnIsRefused` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java:225`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsMssqlIT#singleLookup` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java:180`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsMssqlIT#startupCheckPassesAgainstSqlServer` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java:145`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsMssqlIT#viewsAreReadAndMapped` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java:154`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsMssqlIT#writesAreDenied` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java:201`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java

- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#nullInternalFlagCountsAsInternal` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:190`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#singleLookupsUseTheSameMapperAndScope` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:283`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#startupCheckPassesWhenColumnsEqualTheWhitelist` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:350`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#startupCheckRefusesAnExtraColumn` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:359`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#startupCheckRefusesMissingAndExtraObjects` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:371`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#startupCheckTreatsBaseTablesAsSubset` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:109`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#startupCheckVerifiesTheRootPath` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:402`
- `com.forcitis.one.platform.integration.infrastructure.seeyon.SeeyonViewsOnPostgresTest#unitTypeIsReadFromTheBaseTableAndChecked` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsOnPostgresTest.java:91`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java

- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#configuredDuplicateTaskErrorCountsAsSent` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:254`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#failureMessagesHoldNoCredentials` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:285`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#failuresAreUnavailable` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:182`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#missingResponseCodeIsEmpty` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:173`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#rejectedTokenIsReplacedOnce` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:130`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#replacementUsesTheResponseCode` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:266`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#secondRejectionFails` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:147`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#sendReturnsTheResponseCode` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:157`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#slowAnswerTimesOut` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:197`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#tokenIsCached` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:98`
- `com.forcitis.one.platform.integration.infrastructure.wecom.HttpWecomClientTest#tokenIsRenewedFiveMinutesBeforeExpiry` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/HttpWecomClientTest.java:112`

### backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/WecomConfigurationTest.java

- `com.forcitis.one.platform.integration.infrastructure.wecom.WecomConfigurationTest#disabledClientRefusesEverything` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/WecomConfigurationTest.java:60`
- `com.forcitis.one.platform.integration.infrastructure.wecom.WecomConfigurationTest#enabledNeedsCredentials` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/WecomConfigurationTest.java:70`
- `com.forcitis.one.platform.integration.infrastructure.wecom.WecomConfigurationTest#shippedDefaultsAreSafe` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/WecomConfigurationTest.java:45`
- `com.forcitis.one.platform.integration.infrastructure.wecom.WecomConfigurationTest#timeoutsDefaultToFiveSeconds` — `backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/wecom/WecomConfigurationTest.java:36`

### backend/src/test/java/com/forcitis/one/scenario/LegalEntityAttributionIntegrationTest.java

- `com.forcitis.one.scenario.LegalEntityAttributionIntegrationTest#changeClosesTheVersion` — `backend/src/test/java/com/forcitis/one/scenario/LegalEntityAttributionIntegrationTest.java:143`
- `com.forcitis.one.scenario.LegalEntityAttributionIntegrationTest#endpoints` — `backend/src/test/java/com/forcitis/one/scenario/LegalEntityAttributionIntegrationTest.java:200`
- `com.forcitis.one.scenario.LegalEntityAttributionIntegrationTest#firstVersions` — `backend/src/test/java/com/forcitis/one/scenario/LegalEntityAttributionIntegrationTest.java:130`
- `com.forcitis.one.scenario.LegalEntityAttributionIntegrationTest#resolution` — `backend/src/test/java/com/forcitis/one/scenario/LegalEntityAttributionIntegrationTest.java:164`
- `com.forcitis.one.scenario.LegalEntityAttributionIntegrationTest#unresolvedFails` — `backend/src/test/java/com/forcitis/one/scenario/LegalEntityAttributionIntegrationTest.java:185`
- `com.forcitis.one.scenario.LegalEntityAttributionIntegrationTest#writesNeedTheVersion` — `backend/src/test/java/com/forcitis/one/scenario/LegalEntityAttributionIntegrationTest.java:228`

### backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java

- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#orgTree` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:126`
- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#permissionsAreEnforced` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:97`
- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#reconciliationOnDemand` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:284`
- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#releaseNeedsAReason` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:315`
- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#releaseReasonIsBounded` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:293`
- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#releaseWhileSourceIsDown` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:263`
- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#syncRuns` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:162`
- `com.forcitis.one.scenario.WorkforceHttpIntegrationTest#syncRunsPageByStartTimeAndId` — `backend/src/test/java/com/forcitis/one/scenario/WorkforceHttpIntegrationTest.java:190`

## 有规则无测试（221 条）

来源：`docs/modernization/architecture/ssot/c1-rules/rules.yaml` 与 `docs/modernization/rules/*.md` 中全部 RULE-/INV- 编号（含正文引用）。

### 新规则卡（RULE-316 及以上）（47 条）

前 30 个编号（按数字排序）：

- `RULE-325`
- `RULE-328`
- `RULE-337`
- `RULE-340`
- `RULE-352`
- `RULE-368`
- `RULE-369`
- `RULE-370`
- `RULE-371`
- `RULE-376`
- `RULE-377`
- `RULE-378`
- `RULE-379`
- `RULE-380`
- `RULE-381`
- `RULE-382`
- `RULE-384`
- `RULE-387`
- `RULE-388`
- `RULE-390`
- `RULE-391`
- `RULE-392`
- `RULE-393`
- `RULE-394`
- `RULE-395`
- `RULE-396`
- `RULE-397`
- `RULE-398`
- `RULE-399`
- `RULE-400`

### 老行为卡（RULE-315 及以下）（22 条）

前 30 个编号（按数字排序）：

- `RULE-01`
- `RULE-02`
- `RULE-03`
- `RULE-04`
- `RULE-05`
- `RULE-06`
- `RULE-07`
- `RULE-08`
- `RULE-022`
- `RULE-070`
- `RULE-076`
- `RULE-077`
- `RULE-078`
- `RULE-079`
- `RULE-080`
- `RULE-081`
- `RULE-089`
- `RULE-114`
- `RULE-255`
- `RULE-256`
- `RULE-257`
- `RULE-265`

### INV（152 条）

前 30 个编号（按数字排序）：

- `INV-01`
- `INV-02`
- `INV-03`
- `INV-03a`
- `INV-03b`
- `INV-03c`
- `INV-04`
- `INV-04a`
- `INV-04b`
- `INV-05`
- `INV-06`
- `INV-07`
- `INV-08`
- `INV-09`
- `INV-10`
- `INV-11`
- `INV-12`
- `INV-13`
- `INV-14`
- `INV-15`
- `INV-16`
- `INV-16a`
- `INV-16b`
- `INV-17`
- `INV-18`
- `INV-19`
- `INV-20`
- `INV-21`
- `INV-22`
- `INV-23`

