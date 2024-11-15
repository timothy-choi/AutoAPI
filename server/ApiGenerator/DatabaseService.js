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

exports.addModelTableInfo = async (databaseId, modelTableInfo, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.ModelTablesInfo.push(modelTableInfo);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.removeModelTableInfoUsed = async (databaseId, modelTableInfo, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.ModelTablesInfo.splice(database.ModelTablesInfo.indexOf(modelTableInfo), 1);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.editModelTableInfo = async (databaseId, modelTableInfoId, updatedModelTableInfo, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Error('Model does not exist');
        } 

        var index = database.ModelTablesInfo.findIndex(modelTableInfo = modelTableInfo.id != modelTableInfoId);

        database.ModelTablesInfo.splice(index, 1);

        database.ModelTablesInfo.splice(index, 0, updatedModelTableInfo);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.ModifyDatabaseInstanceInfo = async (databaseId, databaseInstanceInfo, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Error('Model does not exist');
        } 

        database.ModelDatabaseInstanceInfo = databaseInstanceInfo;

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.SetHealthStatus = async (databaseId, healthStatus, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Error('Model does not exist');
        } 

        database.HealthStatus = healthStatus;

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.SetStatus = async (databaseId, databaseStatus, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Error('Model does not exist');
        } 

        database.Status = databaseStatus;

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.AddDatabaseChangeLog = async (databaseId, databaseChangeLog) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.DatabaseChangeLog.push(databaseChangeLog);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.AddDatabaseOperationsLog = async (databaseId, databaseOperationsLog) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.DatabaseOperationsLog.push(databaseOperationsLog);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.ModifyDatabaseBackupInfo = async (databaseId, databaseBackupInfo, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Error('Model does not exist');
        } 

        database.DatabaseBackupInfo = databaseBackupInfo;

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.AddDatabaseOperationsLog = async (databaseId, databaseOperationsLog) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Exception('database does not exist');
        }

        database.DatabaseOperationsLog.push(databaseOperationsLog);

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.ModifyDatabaseCloudInfo = async (databaseId, databaseCloudInfo, username) => {
    try {
        var database = await this.getDatabaseById(databaseId);

        if (!database) {
            throw new Error('Model does not exist');
        } 

        database.DatabaseCloudInfo = databaseCloudInfo;

        database.DidUpdate = true;

        database.UpdatedAt = Date.now();

        database.UpdatedBy = username;

        await database.save();
    } catch (error) {
        throw new Error('could not delete model');
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