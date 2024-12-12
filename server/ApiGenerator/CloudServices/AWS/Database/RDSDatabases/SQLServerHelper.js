const mssql = require('mssql');

exports.connectToSQLServerDatabase = async (connectionInfo) => {
    try {
        const pool = new mssql.ConnectionPool(connectionInfo);

        const poolInstance = await pool.connect();

        return (pool, poolInstance);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.executeQuery = async (pool, query, params = []) => {
    try {
        const request = pool.request();

        params.forEach(({ name, value }) => request.input(name, value));

        const response = await request.query(query);

        return response.recordset;
    } catch (error) {
        throw new Error(error.message);
    } finally {
        
    }
}

exports.insertModifyOrDeleteInSqlServer = async (pool, query, params = []) => {
    try {
        const request = pool.request();

        params.forEach(({ name, value }) => request.input(name, value));

        const response = await request.query(query);

        return response.rowsAffected;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.endSqlServerConnection = async (pool) => {
    try {
        await pool.close();
    } catch (error) {
        throw new Error(error.message);
    } 
}