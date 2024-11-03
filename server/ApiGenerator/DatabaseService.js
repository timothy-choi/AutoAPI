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

exports.CreateDatabase = async (databaseInfo) => {
    var database = await DatabaseDefinition.findOne({Name: databaseInfo.Name});

    if (database) {
        throw new Exception('Database already exists');
    }

    database = await DatabaseDefinition.Create({Name:  databaseInfo.Name, 
                                                Type: databaseInfo.Type, 
                                                Version: databaseInfo.Version, 
                                                Description: databaseInfo.Description,
                                                CreatedAt: Date.now(),
                                                CreatedBy: databaseInfo.CreatedBy,
                                                ModelsUsed: databaseInfo.ModelsUsed });
    
    return database;
}

exports.EditDescription = async (databaseId, desc, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.Description = desc;

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.addModelsUsed = async (databaseId, model, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.ModelsUsed.push(model);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.removeModelsUsed = async (databaseId, model, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.ModelsUsed.splice(database.ModelsUsed.indexOf(model), 1);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.DeleteDatabase = async (databaseId) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        await database.destroy();
    } catch (error) {
        throw new Exception('Can not delete database');
    }
}