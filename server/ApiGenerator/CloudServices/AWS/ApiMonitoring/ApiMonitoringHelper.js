const AWS = require('aws-sdk');

exports.createTrail = async (userCredentials, trailInfo, userRegion) => {
    try {
        const cloudTrail = new AWS.CloudTrail({userCredentials, region: userRegion});

        const trail = await cloudTrail.createTrail(trailInfo).promise();

        return trail;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.startLogging = async (userCredentials, trailName, userRegion) => {
    try {
        const cloudTrail = new AWS.CloudTrail({userCredentials, region: userRegion});

        const logging = await cloudTrail.startLogging({Name: trailName}).promise();

        return logging;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.stopLogging = async (userCredentials, trailName, userRegion) => {
    try {
        const cloudTrail = new AWS.CloudTrail({userCredentials, region: userRegion});

        const logging = await cloudTrail.stopLogging({Name: trailName}).promise();

        return logging;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};