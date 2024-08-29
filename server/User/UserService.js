const { User } = require('../User/User'); 


exports.GetUserById = async (userId) => {
    var userInfo = await User.findByPk(userId);

    return userInfo;
}

exports.GetUserByUsername = async (usernameValue) => {
    var userInfo = await User.findOne({ where: { username: usernameValue } });

    return userInfo;
}