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