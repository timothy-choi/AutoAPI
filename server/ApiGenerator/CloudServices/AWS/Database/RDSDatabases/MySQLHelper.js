const mysql = require('mysql2');

exports.connectToMySQLDatabase = async (connectionInfo) => {
    try {
        const pool = await mysql.createPool(connectionInfo);

        const poolInstance = await pool.promise();

        return poolInstance;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.endMySQLConnection = async (pool) => {
    try {
        await pool.end();
    } catch (error) {
        throw new Error(error.message);
    } 
}