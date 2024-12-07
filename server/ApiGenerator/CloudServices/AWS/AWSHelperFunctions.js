const AWS = require('aws-sdk');

const axios = require('axios');

exports.createVPC = async (cidrBlock, vpcName, userCredentials, userRegion) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const vpcResponse = await ec2.createVpc({CidrBlock: cidrBlock});

    const vpcId = vpcResponse.Vpc.VpcId;

    const tagParams = {
        Resources: [vpcId],
        Tags: [
            {
                Key: "Name",
                Value: vpcName, 
            },
        ],
    }

    await ec2.createTags(tagParams).promise();

    const modifyVpcParams = {
        VpcId: vpcId,
        EnableDnsSupport: { Value: true },
        EnableDnsHostnames: { Value: true },
    };

    await ec2.modifyVpcAttribute(modifyVpcParams).promise();

    return vpcId;
}

exports.createSecurityGroup = async (vpcId, groupName, groupDesc, inboundRules, userCredentials, userRegion) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const createGroupParams = {
        Description: groupDesc, 
        GroupName: groupName, 
    };

    if (vpcId) {
        createGroupParams.VpcId = vpcId;
    }

    const createGroupResponse = await ec2.createSecurityGroup(createGroupParams).promise();
    const securityGroupId = createGroupResponse.GroupId;

    if (inboundRules) {
        await ec2.authorizeSecurityGroupIngress(inboundRules).promise();
    }

    return securityGroupId;
}

exports.getRegionFromIP = async (ip_addr) => {
    const response = await axios.get('https://ip-ranges.amazonaws.com/ip-ranges.json');

    for (const range of response.data.prefixes) {
        if (ip_addr.startsWith(range.ip_prefix.split('/')[0])) {
            return range.region;
        }
    }

    return null;
}

exports.createScheduledEvent = async (ruleParams, lambdaPermissionParams, targetParams, userCredentials, userRegion) => {
    const eventBridge = new AWS.EventBridge({ credentials: userCredentials, region: userRegion });

    const lambda = new AWS.Lambda({ credentials: userCredentials, region: userRegion });

    try {
        const ruleResponse = await eventBridge.putRule(ruleParams).promise();

        lambdaPermissionParams.SourceArn = ruleResponse.RuleArn;

        await lambda.addPermission(lambdaPermissionParams).promise();

        await eventBridge.putTargets(targetParams).promise();
    } catch (error) {
        throw new Error("Error setting up scheduled event:", error);
    }
};