const fs = require('fs');
const awsHelper = require('../../aws-helper');

const databaseTypes = {
    MySQL: [
      "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
      "FLOAT", "DOUBLE", "DECIMAL", "NUMERIC",
      "CHAR", "VARCHAR", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
      "BINARY", "VARBINARY", "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
      "DATE", "TIME", "DATETIME", "TIMESTAMP", "YEAR",
      "JSON",
      "GEOMETRY", "POINT", "LINESTRING", "POLYGON",
      "ENUM", "SET"
    ],
  
    PostgreSQL: [
      "SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "REAL", "DOUBLE PRECISION",
      "CHAR", "VARCHAR", "TEXT",
      "DATE", "TIME", "TIMESTAMP", "TIMESTAMPTZ", "INTERVAL",
      "BOOLEAN",
      "JSON", "JSONB",
      "ARRAY",
      "ENUM",
      "CIDR", "INET", "MACADDR",
      "TSVECTOR", "TSQUERY",
      "HSTORE",
      "INT4RANGE", "NUMRANGE", "DATERANGE", "TSRANGE", "TSTZRANGE",
      "GEOGRAPHY", "GEOMETRY",
      "UUID"
    ],
  
    MongoDB: [
      "String", "Number", "Boolean", "Array", "Object", "Date", "ObjectId", "Binary", "Decimal128", "BSON", "Embedded Document"
    ],
  
    SQLite: [
      "INTEGER", "TEXT", "REAL", "BLOB", "NUMERIC"
    ],
  
    DynamoDB: [
      "String", "Number", "Binary", "Boolean", "Null", "List", "Map"
    ],

    SQLServer: [
        "bigint", "int", "smallint", "tinyint", "bit",
        "decimal", "numeric", "smallmoney", "money",
        "float", "real",
        "date", "datetime2", "datetime", "datetimeoffset", "smalldatetime", "time",
        "char", "varchar", "text", "nchar", "nvarchar", "ntext",
        "binary", "varbinary", "image",
        "uniqueidentifier",
        "xml",
        "cursor",
        "hierarchyid",
        "sql_variant",
        "table",
        "geometry", "geography",
        "rowversion", "timestamp",
        "sql_variant",
        "table",
        "geometry", "geography",
        "rowversion", "timestamp"
     ],

    CosmosDB: [
        "String", "Number", "Boolean", "Array", "Object", "Date", "ObjectId", "Binary", "Decimal128", "BSON", "Embedded Document"
     ],

    BigTable: [
        "String", "Number", "Boolean", "Array", "Object", "Date", "ObjectId", "Binary", "Decimal128", "BSON", "Embedded Document"
     ]
  };  


const parseAndCheckFile = (jsonFileContent, databaseType) => {
    const data = jsonFileContent;

    var allAttributes = [];

    var prevAttributeNames = [];

    let ct = 0;

    for (var dataAttr in data) {
        if (!('name' in dataAttr) || (dataAttr.name == null) || (typeof(dataAttr.name) != String)) {
            throw new Error('ERROR: attribute name is invalid');
        }

        if (prevAttributeNames.includes(dataAttr.name)) {
            throw new Error('ERROR: attribute name already exists in model');
        }

        if (!('type' in dataAttr) || (dataAttr.type == null) || (typeof(dataAttr.type) != String)) {
            throw new Error('ERROR: attribute type is invalid');
        }

        if (!databaseTypes[databaseType].includes(dataAttr.type)) {
            throw new Error('ERROR: attribute type either does not exist or is incompatible with the database you selected.');
        }

        if (!('required' in dataAttr) || (dataAttr.required == null) || (typeof(dataAttr.required) != Boolean)) {
            throw new Error('ERROR: attribute required is invalid');
        }

        if (!('unique' in dataAttr) || (dataAttr.unique == null) || (typeof(dataAttr.unique) != Boolean)) {
            throw new Error('ERROR: attribute unique is invalid');
        }

        if (dataAttr.required == true && (!('defaultValue' in dataAttr) || dataAttr.defaultValue == null)) {
            throw new Error('ERROR: could not find defaultValue attribute even though it is required');
        }

        if (('defaultValue' in dataAttr && dataAttr.defaultValue == null) || ('defaultValue' in dataAttr && dataAttr.defaultValue != null && typeof(dataAttr.defaultValue) != dataAttr.type)) {
            throw new Error('ERROR: attribute defaultValue is invalid');
        }

        if (!('isPrimaryKey' in dataAttr) || (dataAttr.isPrimaryKey == null) || (typeof(dataAttr.isPrimaryKey) != Boolean)) {
            throw new Error('ERROR: attribute isPrimaryKey is invalid');
        }

        if (!('isForeignKey' in dataAttr) || (dataAttr.isForeignKey == null) || (typeof(dataAttr.isForeignKey) != Boolean)) {
            throw new Error('ERROR: attribute isForeignKey is invalid');
        }

        if (dataAttr.isPrimaryKey == true && dataAttr.isForeignKey == true || dataAttr.isPrimaryKey == true && dataAttr.unique == false) {
            throw new Error('ERROR: attribute isPrimaryKey is true and isForeignKey is true or unique is false');
        }

        if (dataAttr.isPrimaryKey) {
            ct++;
        }

        allAttributes.push(dataAttr);

        prevAttributeNames.push(dataAttr.name);
    }

    if (ct > 1) {
        throw new Error('ERROR: multiple primary keys found in model');
    }

    return allAttributes;
}

const readModelFile = (filename) => {
    fs.readFile(filename, 'utf8', (err, jsonString) => {
        if (err) {
          throw new Error('Error reading file:', err);
        }

        try {
            const data = JSON.parse(jsonString);
            
            return data;
        } catch (err) {
            throw new Error('Error parsing JSON:', err);
        }
    });

}

exports.ProcessModelCreationFile = async (req, res) => {
    try {
        if (!awsHelper.checkFileExists(req.body.bucketName, req.body.key)) {
            return res.status(404).json({ msg: "Not Found" });
        }

        var allInputModelAttributes = [];

        if (req.body.localDownload) {
            var filePath = await awsHelper.localDownloadFile(req.body.bucketName, req.body.key, req.body.filename);

            allInputModelAttributes = readModelFile(filePath);
        } else {
            var data = await awsHelper.downloadFile(req.body.bucketName, req.body.key);

            allInputModelAttributes = JSON.parse(data);
        }

        var allModelAttributes = parseAndCheckFile(allInputModelAttributes);

        return res.status(201).body(allModelAttributes);
    } catch (err) {
        return res.status(500).json({ error: error.message });
    }
}