LOAD DATA INFILE 'D://Nightly_Build//projects//catissuecore/SQL/DBUpgrade/Common/CAModelCSVs/DYEXTN_INTEGER_TYPE_INFO.csv' 
BADFILE '/sample.bad'
DISCARDFILE '/sample.dsc'
APPEND 
INTO TABLE DYEXTN_INTEGER_TYPE_INFO 
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
(IDENTIFIER NULLIF IDENTIFIER='\\N')