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

exports.createSubnet = async (vpcId, cidrBlock, availabilityZone = null, userCredentials, userRegion, mapPublicIpOnLaunch, tags) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        VpcId: vpcId, 
        CidrBlock: cidrBlock,
    };

    if (availabilityZone) {
        params.AvailabilityZone = availabilityZone;
    }

    const result = await ec2.createSubnet(params).promise();

    if (mapPublicIpOnLaunch) {
        await ec2.modifySubnetAttribute({SubnetId: result.Subnet.SubnetId, MapPublicIpOnLaunch: { Value: true },}).promise();
    }

    if (tags > 0) {
        await ec2.createTags({Resources: [subnetId], Tags: tags}).promise();
    }

    return result.Subnet;
}

exports.modifySubnet = async (subnetId, attributes, userCredentials, userRegion) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    for (const [key, value] of Object.entries(attributes)) {
        const params = {
            SubnetId: subnetId,
            [key]: { Value: value }, 
        };

        await ec2.modifySubnetAttribute(params).promise();
    }
}

exports.associateRouteTable = async (subnetId, routeTableId, userCredentials, userRegion) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const result = await ec2.associateRouteTable({SubnetId: subnetId, RouteTableId: routeTableId}).promise();

    return result;
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

exports.updateSecurityGroups = async (userCredentials, userRegion, securityGroupIds) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        GroupIds: securityGroupIds,
    };

    await ec2.modifySecurityGroupRules(params).promise();
}

exports.createNetworkACL = async (vpcId, userCredentials, userRegion) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = { VpcId: vpcId };

    const result = await ec2.createNetworkAcl(params).promise();

    return result.NetworkAcl;
}

exports.addNetworkACLEntry = async (networkACLEntry, userCredentials, userRegion) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    await ec2.createNetworkAclEntry(networkACLEntry).promise();
}

exports.associateNetworkACL = async (subnetId, aclId, userCredentials, userRegion) => {
    const ec2 = new AWS.EC2({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        SubnetId: subnetId,
        NetworkAclId: aclId,
    };

    const result = await ec2.associateNetworkAcl(params).promise();

    return result.AssociationId;
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

exports.getAWSCredentials = async (secretName) => {
    const secretsManager = new AWS.SecretsManager();

    const keyInfo = await secretsManager.getSecretValue({ SecretId: secretName }).promise();

    let secret;
    if (keyInfo.SecretString) {
        secret = JSON.parse(keyInfo.SecretString);
    } else {
        secret = JSON.parse(Buffer.from(keyInfo.SecretBinary, 'base64').toString('utf-8'));
    }

    const accessKey = secret.aws_access_key_id;
    const secretKey = secret.aws_secret_access_key;
    const sessionToken = secret.aws_session_token;

    return {
        accessKeyVal: accessKey,
        secretKeyVal: secretKey,
        sessionTokenVal: sessionToken
    };
}

exports.addTagsToResource = async (awsServiceClient, resourceArn, tags) => {
    const params = {
        ResourceName: resourceArn,
        Tags: tags
    };

    try {
        await awsServiceClient.addTagsToResource(params).promise();

        const data = await awsServiceClient.listTagsForResource({ ResourceName: resourceArn }).promise();
        const tagKeys = data.Tags.map(tag => tag.Key);

        const allTagsAdded = tags.every(tag => tagKeys.includes(tag.Key));
        if (!allTagsAdded) {
            throw new Error(`Some tags were not added to the resource: ${resourceArn}`);
        }

        return data;
    } catch (error) {
        throw new Error(`Error adding tags to resource ${resourceArn}: ${error.message}`);
    }
};

exports.removeTagsFromResource = async (awsServiceClient, resourceArn, tagKeysToRemove) => {
    const params = {
        ResourceName: resourceArn,
        TagKeys: tagKeysToRemove
    };

    try {
        await awsServiceClient.removeTagsFromResource(params).promise();

        const data = await awsServiceClient.listTagsForResource({ ResourceName: resourceArn }).promise();
        const remainingTagKeys = data.Tags.map(tag => tag.Key);

        const allTagsRemoved = tagKeysToRemove.every(tagKey => !remainingTagKeys.includes(tagKey));
        if (!allTagsRemoved) {
            throw new Error(`Some tags were not removed from the resource: ${resourceArn}`);
        }

        return data;
    } catch (error) {
        throw new Error(`Error removing tags from resource ${resourceArn}: ${error.message}`);
    }
};

exports.addSecret = async (secretName, secretValue, region) => {
    try {
        const secretsManager = new AWS.SecretsManager({ region });

        const params = {
            Name: secretName,
            SecretString: JSON.stringify(secretValue), 
        };

        const result = await secretsManager.createSecret(params).promise();

        return result;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.updateSecret = async (secretName, secretValue, region) => {
    try {
      const secretsManager = new AWS.SecretsManager({ region });

      const params = {
        SecretId: secretName,
        SecretString: JSON.stringify(secretValue), 
      };
  
      const result = await secretsManager.putSecretValue(params).promise();

      return result;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteSecret = async (secretName, region) => {
    try {
      const secretsManager = new AWS.SecretsManager({ region });

      const params = {
        SecretId: secretName,
        ForceDeleteWithoutRecovery: true, 
      };
  
      const result = await secretsManager.deleteSecret(params).promise();

      return result;
    } catch (error) {
      console.error('Error deleting secret:', error);
    }
  }