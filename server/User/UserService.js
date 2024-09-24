const { User } = require('../User/User'); 


exports.GetUserById = async (userId) => {
    var userInfo = await User.findByPk(userId);

    return userInfo;
}

exports.GetUserByUsername = async (usernameValue) => {
    var userInfo = await User.findOne({ where: { username: usernameValue } });

    return userInfo;
}

exports.CreateUser = async (userBody) => {
    var userInfo = await User.findOne({ where: { username: userBody['username'] } });

    if (userInfo) {
        throw new Error("User already exists!");
    }

    const user = await User.Create({userAuthId: userBody['userAuthId'], username: userBody['username'], email: userBody['email'], createdAt: Date.now(), githubInfo: userBody['githubInfo'], userDescription: userBody['userDescription']});

    return user;
}

exports.DeleteUser = async (userId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        await userInfo.destroy();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}