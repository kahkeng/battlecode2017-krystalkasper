import numpy
import pandas
from sklearn.ensemble import ExtraTreesClassifier

df = pandas.read_csv('output.csv')
data = numpy.array(df[df.columns[:-1]])
target = numpy.array(df[df.columns[-1]])

model = ExtraTreesClassifier()
model.fit(data, target)
output = numpy.concatenate((numpy.array([df.columns[:-1]]).T, numpy.array([model.feature_importances_]).T), axis=1)
print(output)
