const AWS = require('aws-sdk');

exports.createRDSInstance = async (dbInstanceInfo, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const data = await rds.createDBInstance(dbInstanceInfo).promise();

    return data;
}

exports.describeRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });
    
    const data = await rds.describeDBInstances({DBInstanceIdentifier: currDbId}).promise();

    return data.DBInstances[0];
}

exports.startRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        DBInstanceIdentifier: currDbId
    };

    await rds.startDBInstance(params).promise();
}

exports.stopRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        DBInstanceIdentifier: currDbId
    };

    await rds.stopDBInstance(params).promise();
}


exports.deleteRDSInstance = async (dbId, skipFinalSnapshot = true, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });
    
    const params = {
        DBInstanceIdentifier: dbId,
        SkipFinalSnapshot: skipFinalSnapshot 
    };

    if (!skipFinalSnapshot) {
        params.FinalDBSnapshotIdentifier = `final-snapshot-${Date.now()}`;
    }

    await rds.deleteDBInstance(params).promise();
}