const GithubHelper = require('./GithubHelper');

exports.getGithubUser = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        if (!authHeader) {
            return res.status(401).send('Authorization header missing');
          }
          
          let token;
          
          if (authHeader.startsWith('Bearer ')) {
            token = authHeader.split(' ')[1];
          } else if (authHeader.startsWith('token ')) {
            token = authHeader.split(' ')[1];
          } else {
            return res.status(400).send('Invalid Authorization header format');
          }
      
          if (!token) {
            return res.status(401).send('Token not found');
          }

        const githubUser = await GithubHelper.getGithubUser(token);

        return res.status(200).send(githubUser);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getSpecificGithubUser = async (req, res) => {
    try {
        const githubUser = await GithubHelper.getSpecificGithubUser(req.username);

        return res.status(200).send(githubUser);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getGithubRepo = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        if (!authHeader) {
            return res.status(401).send('Authorization header missing');
        }
        
        let token;
        
        if (authHeader.startsWith('Bearer ')) {
            token = authHeader.split(' ')[1];
        } else if (authHeader.startsWith('token ')) {
            token = authHeader.split(' ')[1];
        } else {
            return res.status(400).send('Invalid Authorization header format');
        }
    
        if (!token) {
            return res.status(401).send('Token not found');
        }

        const githubRepo = await GithubHelper.getGithubRepo(token, req.owner, req.repo);

        return res.status(200).send(githubRepo);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.createGithubRepo = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        if (!authHeader) {
            return res.status(401).send('Authorization header missing');
        }
        
        let token;
        
        if (authHeader.startsWith('Bearer ')) {
            token = authHeader.split(' ')[1];
        } else if (authHeader.startsWith('token ')) {
            token = authHeader.split(' ')[1];
        } else {
            return res.status(400).send('Invalid Authorization header format');
        }
    
        if (!token) {
            return res.status(401).send('Token not found');
        }

        const githubRepo = await GithubHelper.createGithubRepo(token, req.body.repoInfo);

        return res.status(201).send(githubRepo);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};