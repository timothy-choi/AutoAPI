const mysql = require('mysql2/promise');

exports.connectToMySQLDatabase = async (connectionInfo) => {
    try {
        const connection = await mysql.createConnection(connectionInfo);

        return connection;
    } catch (error) {
        throw new Error(error.message);
    }
}