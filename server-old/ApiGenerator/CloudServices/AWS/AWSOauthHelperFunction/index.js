const AWS = require('aws-sdk');
const iam = new AWS.IAM();

exports.handler = async (event) => {
    const username = event.request.userAttributes.email || event.request.userAttributes.sub;;

    try {
        const params = {
            UserName: username,  
        };

        await iam.getUser(params).promise();

        return event;
    } catch (error) {
        throw new Error("User does not belong to an AWS account.");
    }
};