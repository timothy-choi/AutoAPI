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

exports.ExecuteQuery = async (query, params, client) => {
    try {
        const result = await client.query(query, params);

        return result.rows;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.InsertIntoPostgres = async (query, params, client) => {
    try {
        const result = await client.query(query, params);

        return result.rows[0].id;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.RemoveOrModifyInPostgres = async (query, params, client) => {
    try {
        const result = await client.query(query, params);

        return result.rowCount;
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