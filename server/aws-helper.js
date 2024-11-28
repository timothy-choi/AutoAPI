const AWS = require('aws-sdk');

const s3 = new AWS.S3({
    region: 'us-west-1', 
    accessKeyId: process.env.AWS_ACCESS_KEY_ID, 
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY 
});

exports.createBucket = async (bucketName) => {
    const params = {
      Bucket: bucketName,
      CreateBucketConfiguration: {
        LocationConstraint: 'us-west-1'
      }
    };
  
    try {
      await s3.createBucket(params).promise();
    } catch (error) {
      console.error('Error creating bucket:', error);
    }
};

exports.deleteBucket = async (bucketName) => {
    try {
      const listParams = {
        Bucket: bucketName
      };
  
      const listedObjects = await s3.listObjectsV2(listParams).promise();
  
      if (listedObjects.Contents.length > 0) {
        const deleteParams = {
          Bucket: bucketName,
          Delete: {
            Objects: listedObjects.Contents.map(item => ({ Key: item.Key }))
          }
        };
        await s3.deleteObjects(deleteParams).promise();
      }
  
      const deleteBucketParams = {
        Bucket: bucketName
      };
      await s3.deleteBucket(deleteBucketParams).promise();
    } catch (error) {
      throw new Error('Error deleting bucket');
    }
}; 

const bucketExists = async (bucketName) => {
    try {
      await s3.headBucket({ Bucket: bucketName }).promise();

      return true;
    } catch (error) {
      if (error.code === 'NotFound') {
        return false
      } else {
        throw new Error('Error checking bucket existence:', error);
      }
    }
  };
  

