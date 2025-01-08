const passport = require('passport');

exports.authenticateWith = (strategy, options = {}) => {
  return passport.authenticate(strategy, options);
};