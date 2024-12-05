const AWS = require('aws-sdk');

const ec2 = new AWS.EC2();

const createVPC = async (cidrBlock, vpcName) => {
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