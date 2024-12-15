const MySQLHelper = require('./MySQL/MySQLHelper');
const SQLServerHelper = require('./SQLServer/SQLServerHelper');
const PostgreSQLHelper = require('./PostgreSQL/PostgreSQLHelper');
const AzureHealthStatusAndMetricsHelper = require('./AzureRDSMetrics');

exports.CreateDatabaseServer = async (req, res) => {
    try {
        var serverInfo = null;

        if (req.database === 'MySQL') {
            serverInfo = await MySQLHelper.createMySQLServer(req.body.MySqlServerInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            serverInfo = await SQLServerHelper.createSQLServer(req.body.SqlServerInfo, req.body.subscriptionId);
        } else {
            serverInfo = await PostgreSQLHelper.createPostgreSQLServer(req.body.PostgreSQLInfo, req.body.subscriptionId);
        }

        return res.status(201).send({"serverResponse": serverInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.DeleteDatabaseServer = async (req, res) => {
    try {
        if (req.database === 'MySQL') {
            await MySQLHelper.deleteMySQLServer(req.body.resourceGroupName, req.body.serverName, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            await SQLServerHelper.deleteSQLServer(req.body.resourceGroupName, req.body.serverName, req.body.subscriptionId);
        } else {
            await PostgreSQLHelper.deletePostgreSQLServer(req.body.resourceGroupName, req.body.serverName, req.body.subscriptionId);
        }

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateDatabaseServer = async (req, res) => {
    try {
        var updateResponse = null;

        if (req.database === 'MySQL') {
            updateResponse = await MySQLHelper.updateMySQLServer(req.body.serverInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            updateResponse = await SQLServerHelper.updateSQLServer(req.body.serverInfo, req.body.subscriptionId);
        } else {
            updateResponse = await PostgreSQLHelper.updatePostgreSQLServer(req.body.serverInfo, req.body.subscriptionId);
        }

        return res.status(200).send({"updateResponse": updateResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.StartDatabaseServer = async (req, res) => {
    try {
        var startResponse = null;

        if (req.database === 'MySQL') {
            startResponse = await MySQLHelper.startMySQLServer(req.body.serverInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            startResponse = await SQLServerHelper.startSQLServer(req.body.serverInfo, req.body.subscriptionId);
        } else {
            startResponse = await PostgreSQLHelper.startPostgresServer(req.body.serverInfo, req.body.subscriptionId);
        }

        return res.status(200).send({"startResponse": startResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.StopDatabaseServer = async (req, res) => {
    try {
        var stopResponse = null;

        if (req.database === 'MySQL') {
            stopResponse = await MySQLHelper.stopMySQLServer(req.body.serverInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            stopResponse = await SQLServerHelper.stopSQLServer(req.body.serverInfo, req.body.subscriptionId);
        } else {
            stopResponse = await PostgreSQLHelper.startPostgresServer(req.body.serverInfo, req.body.subscriptionId);
        }

        return res.status(200).send({"startResponse": startResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.RebootDatabaseServer = async (req, res) => {
    try {
        var rebootResponse = null;

        if (req.database === 'MySQL') {
            rebootResponse = await MySQLHelper.rebootMySQLServer(req.body.serverInfo, req.body.subscriptionId);
        } else if (req.database === 'Postgres') {
            rebootResponse = await PostgreSQLHelper.rebootPostgresServer(req.body.serverInfo, req.body.subscriptionId);
        }

        return res.status(201).send({"startResponse": startResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.StartDatabaseFailover = async (req, res) => {
    try {
        if (req.database === 'MySQL') {
            await MySQLHelper.failoverFlexibleServer(req.body.resourceGroupName, req.body.serverName, req.body.subscriptionId);
        } else if (req.database === 'Postgres') {
            await PostgreSQLHelper.failoverFlexibleServer(req.body.resourceGroupName, req.body.serverName, req.body.subscriptionId);
        }

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.CreateDatabase = async (req, res) => {
    try {
        var databaseResponse = null;

        if (req.database === 'MySQL') {
            databaseResponse = await MySQLHelper.createMySQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            databaseResponse = await SQLServerHelper.createSQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else {
            databaseResponse = await PostgreSQLHelper.createPostgreSQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        }

        return res.status(201).send({"databaseResponse": databaseResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.GetDatabaseInstanceStatus = async (req, res) => {
    try {
        var databaseResponse = null;

        if (req.database === 'MySQL') {
            databaseResponse = await MySQLHelper.getMySQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            databaseResponse = await SQLServerHelper.getSQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else {
            databaseResponse = await PostgreSQLHelper.getPostgreSQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        }

        return res.status(200).send({"databaseStatus": databaseResponse.status})
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.UpdateDatabase = async (req, res) => {
    try {
        var databaseResponse = null;

        if (req.database === 'MySQL') {
            databaseResponse = await MySQLHelper.updateMySQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            databaseResponse = await SQLServerHelper.updateSQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else {
            databaseResponse = await PostgreSQLHelper.updatePostgresDatabase(req.body.databaseInfo, req.body.subscriptionId);
        }

        return res.status(200).send({"databaseResponse": databaseResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.GetDatabaseInstanceHealthStatus = async (req, res) => {
    try {
        var healthStatusInfo = await AzureHealthStatusAndMetricsHelper.getDatabaseHealthStatus(req.resourceId, req.subscriptionId);

        return res.status(200).send({"healthStatusInfo": healthStatusInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.DeleteDatabase = async (req, res) => {
    try {
        if (req.database === 'MySQL') {
            await MySQLHelper.deleteMySQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else if (req.database === 'SQLServer') {
            await SQLServerHelper.deleteSQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        } else {
            await PostgreSQLHelper.deletePostgreSQLDatabase(req.body.databaseInfo, req.body.subscriptionId);
        }

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}