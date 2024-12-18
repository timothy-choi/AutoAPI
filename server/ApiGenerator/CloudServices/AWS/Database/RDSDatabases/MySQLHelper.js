const mysql = require('mysql2');

exports.connectToMySQLDatabase = async (connectionInfo) => {
    for (let attempt = 1; attempt <= retries; attempt++) {
        try {
            const pool = await mysql.createPool(connectionInfo);

            const poolInstance = await pool.promise();

            return [pool, poolInstance];
        } catch (error) {
            if (attempt == retries) throw new Error(error.message);
        }
    }
}

exports.ExecuteQuery = async (query, params, poolInstance) => {
    try {
        const [results] = await poolInstance.query(query, params);
        return results;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.InsertToMySQLDatabase = async (query, values, poolInstance) => {
    try {
        const [result] = await poolInstance.execute(query, values);

        return result.insertId;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.RemoveOrModifyInMySQLDatabase = async (query, values, poolInstance) => {
    try {
        const [result] = await poolInstance.execute(query, values);

        return result.affectedRows;
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