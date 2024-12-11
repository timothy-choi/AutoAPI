const mssql = require('mssql');

exports.connectToSQLServerDatabase = async (connectionInfo) => {
    try {
        const pool = new mssql.ConnectionPool(connectionInfo);

        const poolInstance = await pool.connect();

        return poolInstance;
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