const MySQLHelper = require('./MySQLHelper');
const SQLServerHelper = require('./SQLServerHelper');
const PostgreSQLHelper = require('./PostgreSQLHelper');


exports.ExecuteQuery = async (req, res) => {
    try {
        var pool = null;

        var queryResponse = null;

        if (req.database === 'MySQL') {
            (pool, poolInstance) = await MySQLHelper.connectToMySQLDatabase(req.body.connectionInfo);

            queryResponse = await MySQLHelper.ExecuteQuery(req.body.query, req.body.params, poolInstance);

            await MySQLHelper.endMySQLConnection(pool);
        } else if (req.database === 'SQLServer') {
            (pool, poolInstance) = await SQLServerHelper.connectToSQLServerDatabase(req.body.connectionInfo);

            queryResponse = await SQLServerHelper.executeQuery(poolInstance, req.body.query, req.body.params);

            await SQLServerHelper.endSqlServerConnection(pool);
        } else {
            (pool, client) = await PostgreSQLHelper.connectToPostgreSQLDatabase(req.body.connectionInfo);

            queryResponse = await PostgreSQLHelper.ExecuteQuery(req.body.query, req.body.params, client);

            await PostgreSQLHelper.endPostgreSQLConnection(pool);
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send("Error executing query: " + error.message);
    }
}