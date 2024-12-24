const { Pool } = require('pg');

exports.connectToPostgreSQLDatabase = async (connectionInfo) => {
    for (let attempt = 1; attempt <= retries; attempt++) {
        try {
            const pool = new Pool(connectionInfo);

            const client = await pool.connect();

            return [pool, client];
        } catch (error) {
            if (attempt == retries) throw new Error(error.message);
        }
    }
}

const ReleaseClient = async (client) => {
    try {
        client.release();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DefineSchema = async (schemaSQL, client) => {
    try {
        const result = await client.query(schemaSQL);

        return result;
    } catch (error) {
        throw new Error(`Failed to define schema: ${error.message}`);
    } finally {
        ReleaseClient(client);
    }
};

exports.ExecuteQuery = async (query, params, client) => {
    try {
        const result = await client.query(query, params);

        return result.rows;
    } catch (error) {
        throw new Error(error.message);
    } finally {
        ReleaseClient(client);
    }
}

exports.InsertIntoPostgres = async (query, params, client) => {
    try {
        const result = await client.query(query, params);

        return result.rows[0].id;
    } catch (error) {
        throw new Error(error.message);
    } finally {
        ReleaseClient(client);
    }
}

exports.RemoveOrModifyInPostgres = async (query, params, client) => {
    try {
        const result = await client.query(query, params);

        return result.rowCount;
    } catch (error) {
        throw new Error(error.message);
    } finally {
        ReleaseClient(client);
    }
}

exports.endPostgreSQLConnection = async (pool) => {
    try {
        await pool.end();
    } catch (error) {
        throw new Error(error.message);
    }
}