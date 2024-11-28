const fs = require('fs');

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
    ]
  };  


const parseAndCheckFile = (jsonFileContent, databaseType) => {
    const data = JSON.parse(jsonFileContent);

    var allAttributes = [];

    var prevAttributeNames = [];

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

        allAttributes.push(dataAttr);

        prevAttributeNames.push(dataAttr.name);
    }

    return allAttributes;
}

const readModelFile = (filename) => {
    fs.readFile(filename, 'utf8', (err, jsonString) => {
        if (err) {
          console.error('Error reading file:', err);
          return;
        }

        try {
            const data = JSON.parse(jsonString);
            
            return data;
        } catch (err) {
            console.error('Error parsing JSON:', err);
        }
    });

}