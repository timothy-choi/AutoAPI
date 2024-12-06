require("dotenv").config();

const AWS_CLIENT_ID = process.env.CLIENT_ID;
const AWS_CLIENT_SECRET = process.env.CLIENT_SECRET;
const AWS_REDIRECT_URI = process.env.REDIRECT_URI;
const AWS_AUTH_URL = process.env.AUTH_URL;
const AWS_TOKEN_URL = process.env.TOKEN_URL;
const AWS_SCOPES = process.env.AWS_SCOPES.split(',').map(scope => scope.trim());
const AWS_IDENTITY_POOL_ID = process.env.AWS_IDENTITY_POOL_ID;
const AWS_COGNITO_USER_POOL = process.env.AWS_COGNITO_USER_POOL;
const AWS_LOGOUT_REDIRECT_URI = process.env.AWS_LOGOUT_REDIRECT_URI;
const AWS_COGNITO_DOMAIN = process.env.AWS_COGNITO_DOMAIN;

const axios = require('axios');

const AWS = require('aws-sdk');

exports.LoginToAWS = async (req, res) => {
    const authorizationUrl = `${AWS_AUTH_URL}?response_type=code&client_id=${AWS_CLIENT_ID}&redirect_uri=${encodeURIComponent(
        AWS_REDIRECT_URI
      )}&scope=${AWS_SCOPES}`;
    
    return res.redirect(authorizationUrl);
}

exports.GetTokenFromAWS = async (req, res) => {
    const authorizationCode = req.query.code;

    if (!authorizationCode) {
        return res.status(400).send("Authorization code not found");
    }

    try {
        const tokenResponse = await axios.post(
            AWS_TOKEN_URL,
            new URLSearchParams({
              grant_type: "authorization_code",
              code: authorizationCode,
              redirect_uri: AWS_REDIRECT_URI,
              client_id: AWS_CLIENT_ID,
              client_secret: AWS_CLIENT_SECRET,
            }),
            { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
        );
        
        if (!tokenResponse.data.access_token || !tokenResponse.data.refresh_token || !tokenResponse.data.id_token) {
            return res.status(500).send("Failed to authenticate");
        }

        return res.status(200).send({"AccessToken": tokenResponse.data.access_token, "RefreshToken": tokenResponse.data.refresh_token, "id_token": tokenResponse.data.id_token});
    } catch (error) {
        return res.status(500).send("Failed to authenticate");
      }
}

exports.refreshAccessToken = async (refreshToken) => {
    try {
        const response = await axios.post(
            AWS_TOKEN_URL,
            new URLSearchParams({
              grant_type: "refresh_token",
              refresh_token: refreshToken,
              client_id: process.env.CLIENT_ID,
              client_secret: process.env.CLIENT_SECRET,
            }),
            { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
        );

        if (!response.data.access_token) {
            throw new Error("Could not get new token");
        }

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getAWSCredentials = async (username, user_region, idToken) => {
    const cognitoIdentity = new AWS.CognitoIdentity();

    try {
      const identityResponse = await cognitoIdentity
        .getId({
          IdentityPoolId: AWS_IDENTITY_POOL_ID,
          Logins: {
            [`cognito-idp.${user_region}.amazonaws.com/${AWS_COGNITO_USER_POOL}`]: idToken,
          },
        })
        .promise();
  
      const { IdentityId } = identityResponse;
  
      const credentialsResponse = await cognitoIdentity
        .getCredentialsForIdentity({
          IdentityId,
          Logins: {
            [`cognito-idp.${user_region}.amazonaws.com/${AWS_COGNITO_USER_POOL}`]: idToken,
          },
        })
        .promise();
    
      var creds = credentialsResponse.Credentials;

      const secretsManager = new AWS.SecretsManager();

      var entryExists = await secretsManager.getSecretValue({SecretId: `${username}_credentials`}).promise();

      if (entryExists) {
            await secretsManager.updateSecret({
                SecretId: `${username}_credentials`, 
                SecretString: JSON.stringify(credentialsResponse.Credentials) 
            }).promise();
      } else {
            await secretsManager.createSecret({
                Name: `${username}_credentials`,
                Description: 'AWS credentials for my application',
                SecretString: JSON.stringify(credentialsResponse.Credentials) 
            }).promise();
      }
      
      return {region , creds};
    } catch (error) {
      throw new Error("Could not get AWS credentials:", error);
    }
}

exports.LogoutFromAWS = async (req, res) => {
    const { accessToken, idToken } = req.body;

    const cognitoIdentity = new AWS.CognitoIdentityServiceProvider();

    try {
        if (accessToken) {
            await cognitoIdentity.globalSignOut({ AccessToken: accessToken }).promise();
        } else {
            return res.status(400).send("Logout Failed. No access token provided.");
        }

        if (idToken) {
            const cognitoDomain = AWS_COGNITO_DOMAIN; 
            const logoutRedirectUri = AWS_LOGOUT_REDIRECT_URI; 

            const logoutUrl = `https://${cognitoDomain}/logout?client_id=${process.env.CLIENT_ID}&logout_uri=${encodeURIComponent(logoutRedirectUri)}`;

            return res.redirect(logoutUrl);
        }

        return res.status(200).send({ message: "User logged out successfully." });
    } catch (error) {
        return res.status(500).send({ message: "Logout failed.", error: error.message });
    }
}