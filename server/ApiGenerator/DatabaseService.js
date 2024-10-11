const DatabaseDefinition = require('./Database');

exports.getDatabaseById = async (databaseId) => {
    var dbId = mongoose.Types.ObjectId(databaseId);

    var database = await DatabaseDefinition.findById(dbId);

    return database;
};

exports.getDatabaseByName = async (databaseName) => {
    var database = await DatabaseDefinition.findOne({Name: databaseName});

    return database;
};