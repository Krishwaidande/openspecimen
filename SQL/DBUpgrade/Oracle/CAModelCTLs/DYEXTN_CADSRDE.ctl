LOAD DATA INFILE 'D://Nightly_Build//projects//catissuecore/SQL/DBUpgrade/Common/CAModelCSVs/DYEXTN_CADSRDE.csv' 
BADFILE '/sample.bad'
DISCARDFILE '/sample.dsc'
APPEND 
INTO TABLE DYEXTN_CADSRDE 
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
(IDENTIFIER NULLIF IDENTIFIER='\\N',PUBLIC_ID NULLIF PUBLIC_ID='\\N')