const mssql = require('mssql');

exports.connectToSQLServerDatabase = async (connectionInfo) => {
    for (let attempt = 1; attempt <= retries; attempt++) {
        try {
            const pool = new mssql.ConnectionPool(connectionInfo);

            const poolInstance = await pool.connect();

            return [pool, poolInstance];
        } catch (error) {
            if (attempt == retries) throw new Error(error.message);
        }
    }
}

exports.defineSchema = async (pool, schemaSQL) => {
    try {
        const request = pool.request();

        const response = await request.query(schemaSQL);

        return response;
    } catch (error) {
        throw new Error(`Failed to define schema: ${error.message}`);
    }
};


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