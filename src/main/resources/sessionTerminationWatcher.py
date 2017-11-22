__author__ = 'DarkStar1'

######
# The purpose of this script is to detect when a session has been terminated for a document in libreOffice online logs
# and then send a DELETE method request to the document server to notify the server of the session clearance. The REST
# API method called is defined in the https://github.com/magenta-aps/libreoffice-online-repo project.
#
# ------------------------------------------------ Important ------------------------------------------------
# The user that runs this script  must have access to the libreOffice online service log files and also to the directory
# where this script itself will log to. Affects the 'script_log_file_path' variable. It may be also possible to install
# this as a service but this is left as an exercise to the reader.
#
# Note:
#   1 - This script is to be deployed and used on the LOOL server as that is where we're scanning the logs for the
#       particular session termination log output message.
#   2 - It is important that you change the variables, 'document_host_url' and 'log_file_path' to natch your document
#       server's url and the libreOffice online log files path respectively
#   3 - The script_log_file_path determines the log file for which this script logs to. Also subject to change to as
#        required.
######

import json
import logging
import re
import subprocess
import requests


document_host_url = 'https://document-server-url/wopi/session/'
log_file_path = '/path/to/lool/logfile.log'
script_log_file_path = '/path/to/script/logfile.log'
f = subprocess.Popen(['tail', '-F', log_file_path], stdout=subprocess.PIPE, stderr=subprocess.PIPE)

######################################## Logger config ########################################

# configure logger to redirect output to a specific file
logging.basicConfig(level=logging.INFO, filename=script_log_file_path, filemode="a+",
                    format="%(asctime)-15s %(levelname)-8s %(message)s")
# create logger
logger = logging.getLogger('__name__')

################################################################################################

while True:
	log_line = f.stdout.readline().decode().strip()
	pattern = re.search("Have 1 sessions. markToDestroy: true, LastEditableSession: true.", log_line)
	if pattern is not None:
		logger.info('\nFound and processing line' + log_line + '\n')
		# Extract the UUID from the line
		fileId = re.search('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', log_line).group()
		if fileId is None:
			logger.warning('id is not the right length or not found in line')
		elif len(fileId) == 36:
			logger.info('========== Processing for ' + fileId + ' ==========')
			logger.info('Sending request to url {0}{1}'.format(document_host_url, fileId))
			resp = requests.delete(document_host_url + fileId)
			logger.info("Response received:\n\t" + json.dumps(resp.json(), indent=2))
			logger.info('==========================================================================================')
		else:
			logger.warning('We reached a condition that is unexpected with the following line:\n\t' + log_line)
