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

exports.AddApiProjectsViewHistory = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsViewHistory.push(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveApiProjectsViewHistory = async (userId, project) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ApiProjectsViewHistory.remove(project);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddCloudProviderInfo = async (userId, cloudProvider) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.CloudProviderInfo.push(cloudProvider);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveCloudProviderInfo = async (userId, cloudProvider) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.CloudProviderInfo.remove(cloudProvider);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddActivityLog = async (userId, logEntry) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.ActivityLog.push(logEntry);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetCloudProviderDefault = async (userId, cloudProvider) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.CloudProviderDefault = cloudProvider;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddAllReceivedUserProjectInvitations = async (userId, projectInvite) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllReceivedUserProjectInvitations.push(projectInvite);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveAllReceivedUserProjectInvitations = async (userId, projectInvite) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllReceivedUserProjectInvitations.remove(projectInvite);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddAllSentUserProjectInvitations = async (userId, projectInvite) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllSentUserProjectInvitations.push(projectInvite);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveAllSentUserProjectInvitations = async (userId, projectInvite) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllSentUserProjectInvitations.remove(projectInvite);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddAllSentGroupJoinRequests = async (userId, groupRequests) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllSentGroupJoinRequests.push(groupRequests);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveAllSentGroupJoinRequests = async (userId, groupRequests) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllSentGroupJoinRequests.remove(groupRequests);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddAllUserProjectViewRequests = async (userId, viewRequest) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllUserProjectViewRequests.push(viewRequest);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveAllUserProjectViewRequests = async (userId, viewRequest) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.AllUserProjectViewRequests.remove(viewRequest);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetUserGithubInfo = async (userId, githubInfo) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.UserGithubInfo = githubInfo;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetUserStatsId = async (userId, statsId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.UserStatsId = statsId;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetUserDescription = async (userId, userDescription) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.UserDescription = userDescription;

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.AddUserTag = async (userId, userTag) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.UserTags.push(userTag);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.RemoveUserTag = async (userId, userTag) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.UserTags.remove(userTag);

        userInfo.LastActiveAt = Date.now();

        await userInfo.save();
    } catch (error) {
        throw new Error("Error with deleting user");
    }
}

exports.SetNotificationAccountId = async (userId, notificationAccountId) => {
    try {
        var userInfo = await User.findByPk(userId);

        if (!userInfo) {
            throw new Error("Error with finding user");
        }

        userInfo.NotificationAccountId = notificationAccountId;

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