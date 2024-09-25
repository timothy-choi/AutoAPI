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

exports.ReplaceUsername = async (userId, updatedUsername) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.username = updatedUsername;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.ReplaceEmail = async (userId, updatedEmail) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.email = updatedEmail;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetJoinedGroup = async (userId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.GroupJoined = userInfo.GroupJoined ? false : true;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddPastGroupId = async (userId, groupId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.PastGroupIds.push(groupId);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddCurrentGroupId = async (userId, groupId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.CurrentGroupIds.push(groupId);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveCurrentGroupId = async (userId, groupId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.CurrentGroupIds.remove(groupId);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddAllCollaborators = async (userId, collaborators) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllCollaborators.push(collaborators);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetNotificationsOn = async (userId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.NotificationsOn = userInfo.NotificationsOn ? false : true;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetNotificationType = async (userId, notificationType) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.NotificationType = notificationType;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetIsAvailable = async (userId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.IsAvailable = userInfo.IsAvailable ? false : true;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddApiProjectsCreated = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsCreated.push(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveApiProjectsCreated = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsCreated.remove(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddApiProjectsContributed = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsContributed.push(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveApiProjectsContributed = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsContributed.remove(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddCurrentApiProjects = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.CurrentApiProjects.push(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveCurrentApiProjects = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.CurrentApiProjects.remove(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddApiProjectsCreated = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsCreated.push(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveApiProjectsCreated = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsCreated.remove(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddApiProjectsWithAccess = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsWithAccess.push(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveApiProjectsWithAccess = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsWithAccess.remove(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
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