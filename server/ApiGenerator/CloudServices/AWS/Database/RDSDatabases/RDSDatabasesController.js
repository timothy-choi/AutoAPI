const MySQLHelper = require('./MySQLHelper');
const SQLServerHelper = require('./SQLServerHelper');
const PostgreSQLHelper = require('./PostgreSQLHelper');


exports.ExecuteQuery = async (req, res) => {
    var queryResponse = null;

    var pool = null;
    try {

        if (req.database === 'MySQL') {
            [pool, poolInstance] = await MySQLHelper.connectToMySQLDatabase(req.body.connectionInfo);

            queryResponse = await MySQLHelper.ExecuteQuery(req.body.query, req.body.params, poolInstance);
        } else if (req.database === 'SQLServer') {
            [pool, poolInstance] = await SQLServerHelper.connectToSQLServerDatabase(req.body.connectionInfo);

            queryResponse = await SQLServerHelper.executeQuery(poolInstance, req.body.query, req.body.params);
        } else {
            [pool, client] = await PostgreSQLHelper.connectToPostgreSQLDatabase(req.body.connectionInfo);

            queryResponse = await PostgreSQLHelper.ExecuteQuery(req.body.query, req.body.params, client);
        }
    } catch (error) {
        return res.status(500).send("Error executing query: " + error.message);
    } finally {
        if (req.database === 'MySQL') {
            await MySQLHelper.endMySQLConnection(pool);
        } else if (req.database === 'SQLServer') {
            await SQLServerHelper.endSqlServerConnection(pool);
        } else {
            await PostgreSQLHelper.endPostgreSQLConnection(pool);
        }
    }

    return res.status(201).send({"queryResponse": queryResponse});
}

exports.ModifyItemInDBInstance = async (req, res) => {
    var pool = null;

    var response = null;

    try {
        if (req.database === 'MySQL') {
            [pool, client] = await MySQLHelper.connectToMySQLDatabase(req.body.connectionInfo);

            response = await MySQLHelper.RemoveOrModifyInMySQLDatabase(req.body.query, req.body.params, client);
        } else if (req.database === 'SqlServer') {
            [pool, client] = await SQLServerHelper.connectToSQLServerDatabase(req.body.connectionInfo);

            response = await SQLServerHelper.insertModifyOrDeleteInSqlServer(client, req.body.query, req.body.params);
        } else {
            [pool, client] = await PostgreSQLHelper.connectToPostgreSQLDatabase(req.body.connectionInfo);

            response = await PostgreSQLHelper.RemoveOrModifyInPostgres(req.body.query, req.body.params, client);
        }
    } catch (error) {
        return res.status(500).send("Error executing query: " + error.message);
    } finally {
        if (req.database === 'MySQL') {
            await MySQLHelper.endMySQLConnection(pool);
        } else if (req.database === 'SqlServer') {
            await SQLServerHelper.endSqlServerConnection(pool);
        } else {
            await PostgreSQLHelper.endPostgreSQLConnection(pool);
        }
    }

    return res.status(200).send(response);
}

exports.InsertToDBInstance = async (req, res) => {
    var pool = null;
    try {
        if (req.database === 'MySQL') {
            [pool, client] = await MySQLHelper.connectToMySQLDatabase(req.body.connectionInfo);

            await MySQLHelper.InsertToMySQLDatabase(req.body.query, req.body.params, client);
        } else if (req.database === 'SqlServer') {
            [pool, client] = await SQLServerHelper.connectToSQLServerDatabase(req.body.connectionInfo);

            await SQLServerHelper.insertModifyOrDeleteInSqlServer(client, req.body.query, req.body.params);
        } else {
            [pool, client] = await PostgreSQLHelper.connectToPostgreSQLDatabase(req.body.connectionInfo);

            await PostgreSQLHelper.InsertIntoPostgres(req.body.query, req.body.params, client);
        }
    } catch (error) {
        return res.status(500).send("Error executing query: " + error.message);
    } finally {
        if (req.database === 'MySQL') {
            await MySQLHelper.endMySQLConnection(pool);
        } else if (req.database === 'SqlServer') {
            await SQLServerHelper.endSqlServerConnection(pool);
        } else {
            await PostgreSQLHelper.endPostgreSQLConnection(pool);
        }
    }

    return res.status(201).send(null);
}

exports.RemoveItemInDBInstance = async (req, res) => {
    var pool = null;

    var response = null;
    try {
        if (req.database === 'MySQL') {
            [pool, client] = await MySQLHelper.connectToMySQLDatabase(req.body.connectionInfo);

            response = await MySQLHelper.RemoveOrModifyInMySQLDatabase(req.body.query, req.body.params, client);
        } else if (req.database === 'SqlServer') {
            [pool, client] = await SQLServerHelper.connectToSQLServerDatabase(req.body.connectionInfo);

            response = await SQLServerHelper.insertModifyOrDeleteInSqlServer(client, req.body.query, req.body.params);
        } else {
            [pool, client] = await PostgreSQLHelper.connectToPostgreSQLDatabase(req.body.connectionInfo);

            response = await PostgreSQLHelper.RemoveOrModifyInPostgres(req.body.query, req.body.params, client);
        }
    } catch (error) {
        return res.status(500).send("Error executing query: " + error.message);
    } finally {
        if (req.database === 'MySQL') {
            await MySQLHelper.endMySQLConnection(pool);
        } else if (req.database === 'SqlServer') {
            await SQLServerHelper.endSqlServerConnection(pool);
        } else {
            await PostgreSQLHelper.endPostgreSQLConnection(pool);
        }
    }

    return res.status(200).send(response);
}
