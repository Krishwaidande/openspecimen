LOAD DATA INFILE 'D://Nightly_Build//projects//catissuecore/SQL/DBUpgrade/Common/CAModelCSVs/DYEXTN_RULE.csv' 
BADFILE '/sample.bad'
DISCARDFILE '/sample.dsc'
APPEND 
INTO TABLE DYEXTN_RULE 
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
(IDENTIFIER NULLIF IDENTIFIER='\\N',NAME NULLIF NAME='\\N',ATTRIBUTE_ID NULLIF ATTRIBUTE_ID='\\N')