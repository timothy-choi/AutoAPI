const MongoDBInstanceHelper = require('./MongoDBInstanceHelper');
const MongoDBServiceHelper = require('./MongoDBServiceAccount');

exports.CreateServiceAccount = async (req, res) => {
    try {
        const base64Credentials = req.headers.get('Authorization').slice(6);

        var headerInfo = {
            'Authorization': `Bearer ${base64Credentials}`,
            'Content-Type': 'application/json'
        };

        var serviceAccountResponse = await MongoDBServiceHelper.createServiceAccount(req.body.mongoServiceAccountUri, req.body.name, req.body.apiKey, headerInfo);

        return res.status(201).send({"serviceAccountResponse": serviceAccountResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateServiceAccount = async (req, res) => {
    try {
        const base64Credentials = req.headers.get('Authorization').slice(6);

        var headerInfo = {
            'Authorization': `Bearer ${base64Credentials}`,
            'Content-Type': 'application/json'
        };

        var updateResponse = await MongoDBServiceHelper.updateServiceAccount(req.body.projectId, req.body.apiKeyId, headerInfo, req.body.updates);

        return res.status(200).send({"updateResponse": updateResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetServiceAccountInfo = async (req, res) => {
    try {
        const base64Credentials = req.headers.get('Authorization').slice(6);

        var headerInfo = {
            'Authorization': `Bearer ${base64Credentials}`,
            'Content-Type': 'application/json'
        };

        var serviceAccountResponse = await MongoDBServiceHelper.getServiceAccountInfo(req.body.projectId, req.body.apiKeyId, headerInfo);

        return res.status(200).send({"serviceAccount": serviceAccountResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteServiceAccount = async (req, res) => {
    try {
        const base64Credentials = req.headers.get('Authorization').slice(6);

        var headerInfo = {
            'Authorization': `Bearer ${base64Credentials}`,
            'Content-Type': 'application/json'
        };

        await MongoDBServiceHelper.deleteServiceAccount(req.body.projectId, req.body.apiKeyId, headerInfo);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.AddWhitelistEntry = async (req, res) => {
    try {
        const base64Credentials = req.headers.get('Authorization').slice(6);

        var headerInfo = {
            'Authorization': `Bearer ${base64Credentials}`,
            'Content-Type': 'application/json'
        };

        var whitelistEntryResponse = await MongoDBServiceHelper.addWhitelistEntry(req.body.projectId, req.body.apiKeyId, req.body.whitelistEntry, headerInfo);

        return res.status(201).send({"whitelistEntryResponse": whitelistEntryResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.ConnectAccountToProject = async (req, res) => {
    try {
        const base64Credentials = req.headers.get('Authorization').slice(6);

        var headerInfo = {
            'Authorization': `Bearer ${base64Credentials}`,
            'Content-Type': 'application/json'
        };

        var linkingResponse = await MongoDBServiceHelper.linkServiceAccountToProject(req.body.apiKeyId, req.body.projectId, req.body.roles, headerInfo);

        return res.status(201).send({"linkingResponse": linkingResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createMongoDBProject = async (req, res) => {
    try {
        var projectResponse = await MongoDBInstanceHelper.createProject(req.body.apiKey, req.body.projectUri, req.body.name, req.body.organizationId);

        return res.status(201).send({"projectResponse": projectResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteMongoDBProject = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteProject(req.body.projectUri, req.body.apiKey);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createMongoDBCluster = async (req, res) => {
    try {
        var clusterResponse = await MongoDBInstanceHelper.createCluster(req.body.apiKey, req.body.clusterUri, req.body.clusterInfo);

        return res.status(201).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateMongoDBCluster = async (req, res) => {
    try {
        var clusterResponse = await MongoDBInstanceHelper.updateCluster(req.body.apiKey, req.body.clusterUri, req.body.clusterConfig);

        return res.status(200).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.pauseMongoDBCluster = async (req, res) => {
    try {
        var clusterResponse = await MongoDBInstanceHelper.pauseCluster(req.body.apiKey, req.body.clusterUri);

        return res.status(200).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.resumeMongoDBCluster = async (req, res) => {
    try {
        var clusterResponse = await MongoDBInstanceHelper.resumeCluster(req.body.apiKey, req.body.clusterUri);

        return res.status(200).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.scaleMongoDBCluster = async (req, res) => {
    try {
        var clusterResponse = await MongoDBInstanceHelper.scaleCluster(req.body.apiKey, req.body.clusterUri, req.body.scaleParams);

        return res.status(200).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.deleteMongoDBCluster = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteCluster(req.body.apiKey, req.body.clusterUri);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createMongoDBDatabase = async (req, res) => {
    try {
        var projectResponse = await MongoDBInstanceHelper.createDatabase(req.body.apiKey, req.body.dbName, req.body.collectionName, req.body.dbUri);

        return res.status(201).send({"projectResponse": projectResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteMongoDBDatabase = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteDatabase(req.body.databaseUri, req.body.apiKey);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.backupMongoDBDatabase = async (req, res) => {
    try {
        var backupResponse = await MongoDBInstanceHelper.backupDatabase(req.body.databaseUri, req.body.apiKey);

        return res.status(200).send({"backupResponse": backupResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.restoreMongoDBBackup = async (req, res) => {
    try {
        var databaseResponse = await MongoDBInstanceHelper.restoreDatabase(req.body.databaseUri, req.body.backupId, req.body.apiKey);

        return res.status(200).send({"restoreBackupResponse": databaseResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.enableAutomatedBackups = async (req, res) => {
    try {
        await MongoDBInstanceHelper.enableAutomatedBackups(req.body.apiKey, req.body.clusterUri, req.body.backupConfig);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateCollection = async (req, res) => {
    try {
        var collectionResponse = await MongoDBInstanceHelper.createCollection(req.body.apiKey, req.body.collectionParams, req.body.collectionUri);

        return res.status(201).send({"collectionResponse": collectionResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateCollection = async (req, res) => {
    try {
        var collectionResponse = await MongoDBInstanceHelper.updateCollection(req.body.apiKey, req.body.collectionUri, req.body.updates);

        return res.status(200).send({"collectionResponse": collectionResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteCollection = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteCollection(req.body.apiKey, req.body.collectionUri);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateIndex = async (req, res) => {
    try {
        var indexResponse = await MongoDBInstanceHelper.createIndex(req.body.apiKey, req.body.indexUri, req.body.indexDetails);

        return res.status(201).send({"indexResponse": indexResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateIndex = async (req, res) => {
    try {
        var indexResponse = await MongoDBInstanceHelper.updateIndex(req.body.apiKey, req.body.indexUri, req.body.indexConfig);

        return res.status(200).send({"indexResponse": indexResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteIndex = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteIndex(req.body.apiKey, req.body.indexUri);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.AddIPWhitelist = async (req, req) => {
    try {
        var whitelistResponse = await MongoDBInstanceHelper.addIPWhitelist(req.body.apiKey, req.body.projectUri, req.body.ipAddress);

        return res.status(200).send({"whitelistResponse": whitelistResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.EnableEncryptionAtRest = async (req, res) => {
    try {
        var encryptionResponse = await MongoDBInstanceHelper.enableEncryptionAtRest(req.body.apiKey, req.body.projectUri, req.body.encryptionConfig);

        return res.status(200).send({"encryptionResponse": encryptionResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateSecuritySettings = async (req, res) => {
    try {
        var securityResponse = await MongoDBInstanceHelper.updateSecuritySettings(req.body.apiKey, req.body.projectUri, req.body.securityConfig);

        return res.status(200).send({"securityResponse": securityResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateClusterMaintenenceWindow = async (req, res) => {
    try {
        var maintenenceResponse = await MongoDBInstanceHelper.updateClusterMaintenanceWindow(req.body.apiKey, req.body.clusterUri, req.body.maintenanceConfig);

        return res.status(201).send({"maintenenceRespone": maintenenceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.CreateUser = async (req, res) => {
    try {
        var userResponse = await MongoDBInstanceHelper.createDatabaseUser(req.body.apiKey, req.body.projectId, req.body.username, req.body.password, req.body.dbName, req.body.roles);

        return res.status(201).send({"userResponse": userResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateUser = async (req, res) => {
    try {
        var userResponse = await MongoDBInstanceHelper.updateDatabaseUser(req.body.apiKey, req.body.projectId, req.body.username, req.body.roles);

        return res.status(200).send({"userResponse": userResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.DeleteUser = async (req, res) => {
    try {
        await MongoDBInstanceHelper.deleteDatabaseUser(req.body.apiKey, req.body.projectId, req.body.username);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.getMongoDBMetrics = async (req, res) => {
    try {
        var metricsData = await MongoDBInstanceHelper.getMongoDBClusterMetrics(req.body.userRegion, req.body.payloadInfo);

        return res.status(201).send({"metricsData": metricsData});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}