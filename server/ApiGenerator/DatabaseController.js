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

exports.deleteDatabase = async (req, res) => {
    try {
        await DatabaseService.DeleteDatabase(req.databaseId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}