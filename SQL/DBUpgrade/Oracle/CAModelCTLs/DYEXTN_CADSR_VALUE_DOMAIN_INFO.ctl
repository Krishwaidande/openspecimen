LOAD DATA INFILE 'D://Nightly_Build//projects//catissuecore/SQL/DBUpgrade/Common/CAModelCSVs/DYEXTN_CADSR_VALUE_DOMAIN_INFO.csv' 
BADFILE '/sample.bad'
DISCARDFILE '/sample.dsc'
APPEND 
INTO TABLE DYEXTN_CADSR_VALUE_DOMAIN_INFO 
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
(IDENTIFIER NULLIF IDENTIFIER='\\N',DATATYPE NULLIF DATATYPE='\\N',NAME NULLIF NAME='\\N',TYPE NULLIF TYPE='\\N',PRIMITIVE_ATTRIBUTE_ID NULLIF PRIMITIVE_ATTRIBUTE_ID='\\N')