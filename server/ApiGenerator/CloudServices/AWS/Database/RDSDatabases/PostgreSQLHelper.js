const { Client } = require('pg');

exports.connectToPostgreSQLDatabase = async (connectionInfo) => {
    try {
        const client = new Client(connectionInfo);

        await client.connect();

        return client;
    } catch (error) {
        throw new Error(error.message);
    }
}