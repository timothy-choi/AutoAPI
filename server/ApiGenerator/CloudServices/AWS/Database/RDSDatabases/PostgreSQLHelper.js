const { Pool } = require('pg');

exports.connectToPostgreSQLDatabase = async (connectionInfo) => {
    try {
        const pool = new Pool(connectionInfo);

        const client = await pool.connect();

        return client;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.endPostgreSQLConnection = async (pool) => {
    try {
        await pool.end();
    } catch (error) {
        throw new Error(error.message);
    }
}