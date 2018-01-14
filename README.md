ETL:
    postgres ->Google Cloud Storage -> BigQuery -> Elasticsearch
   
scala:  2.12.2   
usage:  
    sbt bq/assembly  
    java -jar bq-assembly-1.0.jar  
    