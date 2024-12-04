require("dotenv").config();

const AWS_CLIENT_ID = process.env.CLIENT_ID;
const AWS_CLIENT_SECRET = process.env.CLIENT_SECRET;
const AWS_REDIRECT_URI = process.env.REDIRECT_URI;
const AWS_AUTH_URL = process.env.AUTH_URL;
const AWS_TOKEN_URL = process.env.TOKEN_URL;

const axios = require('axios');

exports.LoginToAWS = async (req, res) => {
    const authorizationUrl = `${AWS_AUTH_URL}?response_type=code&client_id=${AWS_CLIENT_ID}&redirect_uri=${encodeURIComponent(
        AWS_REDIRECT_URI
      )}&scope=openid email profile`;
    
    res.redirect(authorizationUrl);
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

        return res.status(200).send({"AccessToken": tokenResponse.data.access_token, "RefreshToken": tokenResponse.data.refresh_token});
    } catch (error) {
        return res.status(500).send("Failed to authenticate");
      }
}