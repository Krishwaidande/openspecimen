LOAD DATA INFILE 'D://Nightly_Build//projects//catissuecore/SQL/DBUpgrade/Common/CAModelCSVs/DYEXTN_LIST_BOX.csv' 
BADFILE '/sample.bad'
DISCARDFILE '/sample.dsc'
APPEND 
INTO TABLE DYEXTN_LIST_BOX 
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
(IDENTIFIER NULLIF IDENTIFIER='\\N',MULTISELECT NULLIF MULTISELECT='\\N')