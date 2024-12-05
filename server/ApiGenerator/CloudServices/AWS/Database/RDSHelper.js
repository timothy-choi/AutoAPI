const AWS = require('aws-sdk');

const rds = new AWS.RDS();

const createRDSInstance = async (dbInstanceInfo) => {
    const data = await rds.createDBInstance(dbInstanceInfo).promise();

    return data;
}

const describeRDSInstance = async (currDbId) => {
    const data = await rds.describeDBInstances({DBInstanceIdentifier: currDbId}).promise();

    return data.DBInstances[0];
}

const deleteRDSInstance = async (dbId, skipFinalSnapshot = true) => {
    const params = {
        DBInstanceIdentifier: dbId,
        SkipFinalSnapshot: skipFinalSnapshot 
    };

    if (!skipFinalSnapshot) {
        params.FinalDBSnapshotIdentifier = `final-snapshot-${Date.now()}`;
    }

    await rds.deleteDBInstance(params).promise();
}