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

exports.GetDatabaseServerStatus = async (req, res) => {
    try {
        var serverInfo = null;

        var serverStatus = {};

        if (req.database === 'MySQL') {
            serverInfo = await MySQLHelper.getMySQLServer(req.body.MySqlServerInfo, req.body.subscriptionId);

            serverStatus.state = serverInfo.userVisibleState;
        } else if (req.database === 'SQLServer') {
            serverInfo = await SQLServerHelper.getSQLServer(req.body.SqlServerInfo, req.body.subscriptionId);

            serverStatus.state = serverInfo.state;
        } else {
            serverInfo = await PostgreSQLHelper.getPostgreSQLServer(req.body.PostgreSQLInfo, req.body.subscriptionId);

            serverStatus.state = serverInfo.userVisibleState;
        }

        serverStatus.name = serverInfo.name;

        serverStatus.location = serverInfo.location;

        return res.status(200).send({"serverStatus": serverStatus});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.ListAllServers = async (req, res) => {
    try {
        var allServers = [];

        if (req.database === 'MySQL') {
            allServers = await MySQLHelper.ListMySQLServers(req.subscriptionId);
        } else if (req.database === 'SQLServer') {
            allServers = await SQLServerHelper.ListSQLServers(req.subscriptionId);
        } else {
            allServers = await PostgreSQLHelper.ListPostgresServers(req.subscriptionId);
        }

        return res.status(200).send({"allServers": allServers});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

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

exports.StartDatabaseBackup = async (req, res) => {
    try {
        var backupResponse = null;

        if (req.database === 'MySQL') {
            backupResponse = await MySQLHelper.restoreBackup(req.body.restoreInfo, req.body.subscriptionId);
        } else if (req.database === 'Postgres') {
            backupResponse = await PostgreSQLHelper.restoreBackup(req.body.restoreInfo, req.body.subscriptionId);
        } else {
            backupResponse = await SQLServerHelper.restoreBackup(req.body.restoreInfo, req.body.subscriptionId);
        }

        return res.status(201).send({"backupResponse": backupResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.CreateOrUpdateFirewallRule = async (req, res) => {
    try {
        var firewallResponse = null;

        if (req.database === 'MySQL') {
            firewallResponse = await MySQLHelper.createOrUpdateFirewallRule(req.body.firewallInfo, req.body.subscriptionId);
        } else if (req.database === 'Postgres') {
            firewallResponse = await PostgreSQLHelper.createOrUpdateFirewallRule(req.body.firewallInfo, req.body.subscriptionId);
        } else {
            firewallResponse = await SQLServerHelper.createOrUpdateFirewallRule(req.body.firewallInfo, req.body.subscriptionId);
        }

        return res.status(200).send({"firewallResponse": firewallResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.RemoveFirewallRule = async (req, res) => {
    try {
        if (req.database === 'MySQL') {
            await MySQLHelper.removeFirewallRule(req.body.firewallInfo, req.body.subscriptionId);
        } else if (req.database === 'Postgres') {
            await PostgreSQLHelper.removeFirewallRule(req.body.firewallInfo, req.body.subscriptionId);
        } else {
            await SQLServerHelper.removeFirewallRule(req.body.firewallInfo, req.body.subscriptionId);
        }

        return res.status(200).send(null);
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

exports.GetDatabaseMetrics = async (req, res) => {
    try {
        var metrics = await AzureHealthStatusAndMetricsHelper.getDatabaseMetrics(req.body.metricsFunctionUri, req.body.metricsRequest, req.body.stopVal);

        return res.status(201).send({"metrics": metrics});
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