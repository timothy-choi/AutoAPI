exports.githubAuth = (req, res, next) => {
    next();
};
  
exports.githubCallback = (req, res) => {
    if (req.isAuthenticated() && req.user) {
        req.cookie('accessToken', req.user.accessToken, {
            httpOnly: true,
            secure: true, 
            maxAge: 3600000, 
        });

        req.cookie('refreshToken', req.user.refreshToken, {
            httpOnly: true,
            secure: true, 
            maxAge: 3600000, 
        });

        res.redirect('/');
    }
};