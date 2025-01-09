const passport = require('passport');

exports.refreshAccessToken = async (refreshToken, clientId, clientSecret) => {
    const tokenEndpoint = 'https://github.com/login/oauth/access_token'; 
    try {
      const response = await axios.post(tokenEndpoint, {
        client_id: clientId,
        client_secret: clientSecret,
        refresh_token: refreshToken,
        grant_type: 'refresh_token',
      }, {
        headers: { Accept: 'application/json' },
      });

      return response.data; 
    } catch (error) {
      throw new Error('Failed to refresh token.', error);
    }
};

exports.isTokenExpired = async (token) => {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf8'));
    const currentTime = Math.floor(Date.now() / 1000);
    return currentTime >= payload.exp;
};
  

exports.authenticateWith = (strategy, options = {}) => {
    return async (req, res, next) => {
        try {
          if (req.isAuthenticated()) {
            const { accessToken, refreshToken, clientId, clientSecret } = req.user;
            if (isTokenExpired(accessToken)) {
              const newTokens = await refreshAccessToken(refreshToken, clientId, clientSecret);
              req.user.accessToken = newTokens.access_token;
            }
          }
          passport.authenticate(strategy, options)(req, res, next);
        } catch (error) {
          throw new Error(error.message);   
        }
      };
};