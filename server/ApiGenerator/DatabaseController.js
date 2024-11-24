const DatabaseService = require('./DatabaseService');

exports.getDatabaseById = async (req, res) => {
    try {
        var database = await DatabaseService.getDatabaseById(req.databaseId);

        return res.status(200).body(database);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.getDatabaseByName = async (req, res) => {
    try {
        var database = await DatabaseService.getDatabaseByName(req.databaseName);

        return res.status(200).body(database);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.createDatabase = async (req, res) => {
    try {
        var database = await DatabaseService.CreateDatabase(req.body);

        return res.status(201).json(database);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.editDatabaseDescription = async (req, res) => {
    try {
        await DatabaseService.EditDescription(req.databaseId, req.body.desc, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.addModelsUsed = async (req, res) => {
    try {
        await DatabaseService.addModelsUsed(req.databaseId, req.model, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.removeModelsUsed = async (req, res) => {
    try {
        await DatabaseService.removeModelsUsed(req.databaseId, req.model, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.addModelTableInfo = async (req, res) => {
    try {
        await DatabaseService.addModelTableInfo(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.removeModelTableInfo = async (req, res) => {
    try {
        await DatabaseService.removeModelTableInfoUsed(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.editModelTableInfo = async (req, res) => {
    try {
        await DatabaseService.editModelTableInfo(req.databaseId, req.modelTableInfoId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.modifyDatabaseInstanceInfo = async (req, res) => {
    try {
        await DatabaseService.ModifyDatabaseInstanceInfo(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.setHealthStatus = async (req, res) => {
    try {
        await DatabaseService.SetHealthStatus(req.databaseId, req.healthStatus, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.setStatus = async (req, res) => {
    try {
        await DatabaseService.SetStatus(req.databaseId, req.databaseStatus, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.addDatabaseChangeLog = async (req, res) => {
    try {
        await DatabaseService.AddDatabaseChangeLog(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.addDatabaseOperationsLog = async (req, res) => {
    try {
        await DatabaseService.AddDatabaseOperationsLog(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.modifyDatabaseBackupInfo = async (req, res) => {
    try {
        await DatabaseService.ModifyDatabaseBackupInfo(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.addDatabaseVersionHistory = async (req, res) => {
    try {
        await DatabaseService.AddDatabaseVersionHistory(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.modifyDatabaseCloudInfo = async (req, res) => {
    try {
        await DatabaseService.ModifyDatabaseCloudInfo(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.addServerlessFunction = async (req, res) => {
    try {
        await DatabaseService.AddServerlessFunction(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.removeServerlessFunction = async (req, res) => {
    try {
        await DatabaseService.RemoveServerlessFunction(req.databaseId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.editServerlessFunction = async (req, res) => {
    try {
        await DatabaseService.editServerlessFunction(req.databaseId, req.serverlessFunctionId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.deleteDatabase = async (req, res) => {
    try {
        await DatabaseService.DeleteDatabase(req.databaseId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}