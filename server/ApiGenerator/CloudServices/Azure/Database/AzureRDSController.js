const MySQLHelper = require('./MySQL/MySQLHelper');
const SQLServerHelper = require('./SQLServer/SQLServerHelper');
const PostgreSQLHelper = require('./PostgreSQL/PostgreSQLHelper');

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