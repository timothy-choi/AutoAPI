exports.githubAuth = (req, res, next) => {
    next();
};
  
exports.githubCallback = (req, res) => {
    res.redirect('/');
};