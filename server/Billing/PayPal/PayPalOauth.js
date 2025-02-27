require("dotenv").config();
const axios = require("axios");

exports.loginToPayPal = async (req, res) => {
    const paypalAuthUrl = `${process.env.PAYPAL_AUTH_URL}?client_id=${process.env.PAYPAL_CLIENT_ID}&response_type=code&scope=email openid&redirect_uri=${process.env.PAYPAL_REDIRECT_URI}`;

    res.redirect(paypalAuthUrl);
};

exports.oauthCallback = async (req, res) => {
    const authCode = req.query.code;
    
    if (!authCode) return res.status(400).send("Authorization failed");
  
    try {
      const tokenResponse = await axios.post(
        process.env.PAYPAL_TOKEN_URL,
        new URLSearchParams({
          grant_type: "authorization_code",
          code: authCode,
        }),
        {
          auth: {
            username: process.env.PAYPAL_CLIENT_ID,
            password: process.env.PAYPAL_CLIENT_SECRET,
          },
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
        }
      );
  
      const accessToken = tokenResponse.data.access_token;
  
      return res.status(201).json({
        message: "PayPal account linked successfully",
        token: accessToken,
      });
    } catch (error) {
      res.status(500).json({ error: "Failed to link PayPal account" });
    }
};
