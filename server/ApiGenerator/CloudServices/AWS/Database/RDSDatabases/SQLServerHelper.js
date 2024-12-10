const mssql = require('mssql');

exports.connectToSQLServerDatabase = async (connectionInfo) => {
    try {
        const pool = await mssql.connect(connectionInfo);

        return pool;
    } catch (error) {
        throw new Error(error.message);
    }
}