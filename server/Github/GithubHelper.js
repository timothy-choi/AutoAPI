const { OctoKit } = require('@octokit/rest');

exports.getGithubUser = async (accessToken) => {
    const octokit = new OctoKit({ auth: accessToken });

    try {
        const response = await octokit.rest.users.getAuthenticated();
        
        return response.data;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.getSpecificGithubUser = async (username) => {
    const octokit = new OctoKit();

    try {
        const response = await octokit.rest.users.getByUsername({ username });

        return response.data;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.getGithubRepo = async (accessToken, owner, repo) => {
    const octokit = new OctoKit({ auth: accessToken });

    try {
        const response = await octokit.rest.repos.get({ owner, repo });

        return response.data;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createGithubRepo = async (accessToken, repoInfo) => {
    const octokit = new OctoKit({ auth: accessToken });

    try {
        const response = await octokit.rest.repos.createForAuthenticatedUser(repoInfo);

        return response.data;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.addCollaboratorToRepo = async (accessToken, owner, repo, username, permission) => {
    const octokit = new OctoKit({ auth: accessToken });

    try {
        const response = await octokit.rest.repos.addCollaborator({ owner, repo, username, permission });

        return response.data;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};